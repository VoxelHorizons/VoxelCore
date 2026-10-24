package org.voxelhorizons.pack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Vanilla Java models and textures (from the official client jar, or an extracted assets
 * folder) for Bedrock conversion. Furniture models commonly reference vanilla textures such
 * as minecraft:block/oak_planks that content packs do not ship; without them the icon and
 * geometry converters cannot render those items. Content-pack assets always win.
 */
public final class VanillaAssets {
    private VanillaAssets() {}

    /** True for files the Bedrock converters read: vanilla model JSON and PNG textures. */
    static boolean wanted(String name) {
        return (name.startsWith("assets/minecraft/models/") && name.endsWith(".json"))
                || (name.startsWith("assets/minecraft/textures/") && name.endsWith(".png"));
    }

    /** @param source a client .jar/.zip, or a folder containing assets/minecraft/... */
    public static Map<String, byte[]> load(Path source) throws IOException {
        return load(source, BuildProgress.NONE);
    }

    public static Map<String, byte[]> load(Path source, BuildProgress progress) throws IOException {
        Map<String, byte[]> out = new TreeMap<String, byte[]>();
        if (source == null) return out;
        progress.report("Vanilla assets: reading models and textures from " + source.getFileName());
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            try (Stream<Path> files = Files.walk(source)) {
                for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                    String name = source.relativize(file).toString().replace('\\', '/');
                    if (wanted(name)) out.put(name, Files.readAllBytes(file));
                }
            }
            return out;
        }
        if (!Files.isRegularFile(source)) throw new IOException("Vanilla assets not found at " + source);
        try (ZipFile zip = new ZipFile(source.toFile())) {
            BuildProgress.Step step = progress.step("Vanilla assets: scanning client jar entries", zip.size());
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                step.advance(1);
                if (entry.isDirectory() || !wanted(entry.getName()) || entry.getName().contains("..")) continue;
                try (InputStream input = zip.getInputStream(entry)) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) bytes.write(buffer, 0, read);
                    out.put(entry.getName(), bytes.toByteArray());
                }
            }
            step.finish(out.size() + " vanilla models/textures available");
        }
        return out;
    }
}
