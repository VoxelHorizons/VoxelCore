package org.voxelhorizons.pack;

import org.voxelhorizons.content.load.ContentLoader;
import org.voxelhorizons.content.pack.ContentPack;
import org.voxelhorizons.content.pack.ContentPackDiscovery;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Deterministic Java resource-pack compiler for VoxelCore authored packs. */
public final class JavaPackCompiler {
    private final JavaPackCompilerEngine engine;
    private final ContentPackDiscovery packDiscovery;

    public JavaPackCompiler() {
        this(new ContentPackDiscovery(), new ContentLoader());
    }

    JavaPackCompiler(ContentPackDiscovery packDiscovery, ContentLoader contentLoader) {
        this.packDiscovery = packDiscovery;
        this.engine = new JavaPackCompilerEngine(packDiscovery, contentLoader);
    }

    public JavaPackBuildResult compile(Path contentRoot, Path outputZip, Path allocationManifestPath, JavaPackTarget target) {
        return compileWithAllAuthoredAssets(contentRoot, outputZip, allocationManifestPath, target, true);
    }

    public UiGlyphRegistry loadUiGlyphs(Path contentRoot, Path allocationManifestPath, boolean persist) {
        return new UiGlyphLoader().load(contentRoot, allocationManifestPath, persist);
    }

    /** Runs the complete pack validation/compiler pipeline without mutating the manifest or writing a ZIP. */
    public JavaPackBuildResult validate(Path contentRoot, Path allocationManifestPath, JavaPackTarget target) {
        return compileWithAllAuthoredAssets(contentRoot, null, allocationManifestPath, target, false);
    }

    /**
     * The compiler engine owns generated resources and the pack namespace used by VoxelCore content.
     * A normal resource pack may also contain completely independent assets, including overrides in
     * {@code assets/minecraft}. Keep those files out of the engine's namespace guard, then merge them
     * into the final deterministic ZIP unchanged.
     */
    private JavaPackBuildResult compileWithAllAuthoredAssets(Path contentRoot, Path outputZip,
                                                              Path allocationManifestPath,
                                                              JavaPackTarget target,
                                                              boolean writeOutput) {
        List<ContentPack> packs = packDiscovery.discover(contentRoot);
        List<AuthoredAsset> additionalAssets = collectAdditionalAssets(packs);
        if (additionalAssets.isEmpty()) {
            return engine.compile(contentRoot, outputZip, allocationManifestPath, target, writeOutput);
        }

        Path stagedRoot = null;
        try {
            stagedRoot = Files.createTempDirectory("voxelcore-pack-");
            copyTree(contentRoot, stagedRoot);
            removeAdditionalNamespaces(contentRoot, stagedRoot, packs);

            JavaPackBuildResult result = engine.compile(stagedRoot, outputZip, allocationManifestPath, target, writeOutput);
            if (writeOutput) mergeAdditionalAssets(outputZip, additionalAssets);
            return new JavaPackBuildResult(result.output(), result.allocationManifest(), result.renderedItems(),
                    result.copiedAssets() + additionalAssets.size());
        } catch (IOException exception) {
            throw new JavaPackCompileException("Unable to stage authored resource-pack assets: " + exception.getMessage(), exception);
        } finally {
            if (stagedRoot != null) deleteTreeQuietly(stagedRoot);
        }
    }

    private static List<AuthoredAsset> collectAdditionalAssets(List<ContentPack> packs) {
        List<AuthoredAsset> assets = new ArrayList<AuthoredAsset>();
        for (ContentPack pack : packs) {
            Path assetsRoot = pack.root().resolve("assets");
            if (!Files.exists(assetsRoot)) continue;
            if (!Files.isDirectory(assetsRoot)) {
                throw new JavaPackCompileException("Assets path is not a directory: " + assetsRoot);
            }
            try {
                DirectoryStream<Path> namespaces = Files.newDirectoryStream(assetsRoot);
                try {
                    for (Path namespaceRoot : namespaces) {
                        if (Files.isSymbolicLink(namespaceRoot)) {
                            throw new JavaPackCompileException("Symbolic links are not supported in authored assets: " + namespaceRoot);
                        }
                        if (!Files.isDirectory(namespaceRoot)) {
                            throw new JavaPackCompileException("Resource-pack asset namespace must be a directory: " + namespaceRoot);
                        }
                        String namespace = namespaceRoot.getFileName().toString();
                        if (namespace.equals(pack.manifest().namespace())) continue;

                        List<Path> files = new ArrayList<Path>();
                        collectFiles(namespaceRoot, files);
                        Collections.sort(files, new Comparator<Path>() {
                            @Override public int compare(Path left, Path right) {
                                return left.toString().compareTo(right.toString());
                            }
                        });
                        for (Path file : files) {
                            String relative = namespaceRoot.relativize(file).toString().replace('\\', '/');
                            assets.add(new AuthoredAsset("assets/" + namespace + "/" + relative, file));
                        }
                    }
                } finally {
                    namespaces.close();
                }
            } catch (IOException exception) {
                throw new JavaPackCompileException("Unable to discover authored assets for pack "
                        + pack.manifest().namespace(), exception);
            }
        }
        Collections.sort(assets, new Comparator<AuthoredAsset>() {
            @Override public int compare(AuthoredAsset left, AuthoredAsset right) {
                return left.path.compareTo(right.path);
            }
        });
        return assets;
    }

    private static void removeAdditionalNamespaces(Path originalRoot, Path stagedRoot, List<ContentPack> packs) {
        for (ContentPack pack : packs) {
            Path relativePack = originalRoot.relativize(pack.root());
            Path stagedAssets = stagedRoot.resolve(relativePack).resolve("assets");
            if (!Files.isDirectory(stagedAssets)) continue;
            try {
                DirectoryStream<Path> namespaces = Files.newDirectoryStream(stagedAssets);
                try {
                    for (Path namespaceRoot : namespaces) {
                        if (!namespaceRoot.getFileName().toString().equals(pack.manifest().namespace())) {
                            deleteTree(namespaceRoot);
                        }
                    }
                } finally {
                    namespaces.close();
                }
            } catch (IOException exception) {
                throw new JavaPackCompileException("Unable to prepare staged assets for pack "
                        + pack.manifest().namespace(), exception);
            }
        }
    }

    private static void copyTree(Path sourceRoot, Path targetRoot) throws IOException {
        List<Path> files = new ArrayList<Path>();
        collectFilesForCopy(sourceRoot, files);
        Collections.sort(files, new Comparator<Path>() {
            @Override public int compare(Path left, Path right) {
                return left.toString().compareTo(right.toString());
            }
        });
        for (Path file : files) {
            Path relative = sourceRoot.relativize(file);
            Path target = targetRoot.resolve(relative);
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.copy(file, target);
        }
    }

    private static void collectFilesForCopy(Path root, List<Path> files) throws IOException {
        DirectoryStream<Path> stream = Files.newDirectoryStream(root);
        try {
            for (Path path : stream) {
                if (Files.isSymbolicLink(path)) {
                    throw new JavaPackCompileException("Symbolic links are not supported in content packs: " + path);
                }
                if (Files.isDirectory(path)) collectFilesForCopy(path, files);
                else if (Files.isRegularFile(path)) files.add(path);
            }
        } finally {
            stream.close();
        }
    }

    private static void collectFiles(Path root, List<Path> files) {
        try {
            DirectoryStream<Path> stream = Files.newDirectoryStream(root);
            try {
                for (Path path : stream) {
                    if (Files.isSymbolicLink(path)) {
                        throw new JavaPackCompileException("Symbolic links are not supported in authored assets: " + path);
                    }
                    if (Files.isDirectory(path)) collectFiles(path, files);
                    else if (Files.isRegularFile(path)) files.add(path);
                }
            } finally {
                stream.close();
            }
        } catch (IOException exception) {
            throw new JavaPackCompileException("Unable to discover assets under " + root, exception);
        }
    }

    private static void mergeAdditionalAssets(Path outputZip, List<AuthoredAsset> authoredAssets) {
        TreeMap<String, byte[]> entries = readZip(outputZip);
        for (AuthoredAsset asset : authoredAssets) {
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(asset.source);
            } catch (IOException exception) {
                throw new JavaPackCompileException("Unable to read authored asset " + asset.source, exception);
            }
            byte[] previous = entries.put(asset.path, bytes);
            if (previous != null && !Arrays.equals(previous, bytes)) {
                throw new JavaPackCompileException("Resource pack path collision: " + asset.path);
            }
        }
        writeDeterministicZip(outputZip, entries);
    }

    private static TreeMap<String, byte[]> readZip(Path zipPath) {
        TreeMap<String, byte[]> entries = new TreeMap<String, byte[]>();
        try {
            ZipFile zip = new ZipFile(zipPath.toFile());
            try {
                java.util.Enumeration<? extends ZipEntry> enumeration = zip.entries();
                while (enumeration.hasMoreElements()) {
                    ZipEntry entry = enumeration.nextElement();
                    if (entry.isDirectory()) continue;
                    InputStream input = zip.getInputStream(entry);
                    try {
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
                        entries.put(entry.getName(), output.toByteArray());
                    } finally {
                        input.close();
                    }
                }
            } finally {
                zip.close();
            }
            return entries;
        } catch (IOException exception) {
            throw new JavaPackCompileException("Unable to read generated resource pack " + zipPath, exception);
        }
    }

    private static void writeDeterministicZip(Path output, TreeMap<String, byte[]> entries) {
        try {
            try (OutputStream raw = Files.newOutputStream(output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                 ZipOutputStream zip = new ZipOutputStream(raw)) {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    byte[] bytes = entry.getValue();
                    CRC32 crc = new CRC32();
                    crc.update(bytes);
                    ZipEntry zipEntry = new ZipEntry(entry.getKey());
                    zipEntry.setMethod(ZipEntry.STORED);
                    zipEntry.setSize(bytes.length);
                    zipEntry.setCompressedSize(bytes.length);
                    zipEntry.setCrc(crc.getValue());
                    zipEntry.setTime(0L);
                    zip.putNextEntry(zipEntry);
                    zip.write(bytes);
                    zip.closeEntry();
                }
            }
        } catch (IOException exception) {
            throw new JavaPackCompileException("Unable to write generated resource pack " + output, exception);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        if (Files.isDirectory(root) && !Files.isSymbolicLink(root)) {
            DirectoryStream<Path> stream = Files.newDirectoryStream(root);
            try {
                for (Path child : stream) deleteTree(child);
            } finally {
                stream.close();
            }
        }
        Files.deleteIfExists(root);
    }

    private static void deleteTreeQuietly(Path root) {
        try {
            deleteTree(root);
        } catch (IOException ignored) {
            // Temporary staging cleanup must not hide the compiler result or its original failure.
        }
    }

    private static final class AuthoredAsset {
        private final String path;
        private final Path source;

        private AuthoredAsset(String path, Path source) {
            this.path = path;
            this.source = source;
        }
    }
}
