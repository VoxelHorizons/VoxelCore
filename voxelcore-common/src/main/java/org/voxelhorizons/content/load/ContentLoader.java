package org.voxelhorizons.content.load;

import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.compile.ContentCompileException;
import org.voxelhorizons.content.compile.ItemDefinitionCompiler;
import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.item.RawItemDefinition;
import org.voxelhorizons.content.pack.ContentPack;
import org.voxelhorizons.content.pack.ContentPackDiscovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads authored content packs from disk and compiles their item definitions. */
public final class ContentLoader {
    private final ContentPackDiscovery packDiscovery;
    private final ItemDefinitionParser itemParser;
    private final ItemDefinitionCompiler compiler;

    public ContentLoader() {
        this(new ContentPackDiscovery(), new ItemDefinitionParser(), new ItemDefinitionCompiler());
    }

    ContentLoader(ContentPackDiscovery packDiscovery, ItemDefinitionParser itemParser, ItemDefinitionCompiler compiler) {
        this.packDiscovery = packDiscovery;
        this.itemParser = itemParser;
        this.compiler = compiler;
    }

    public List<RawItemDefinition> loadRaw(Path contentRoot) {
        List<ContentPack> packs = packDiscovery.discover(contentRoot);
        Map<String, ContentPack> packsByNamespace = indexAndValidatePacks(packs);
        Map<ContentID, RawItemDefinition> definitions = new LinkedHashMap<ContentID, RawItemDefinition>();
        Map<ContentID, Path> sources = new LinkedHashMap<ContentID, Path>();

        for (ContentPack pack : packs) {
            Path authoredRoot = pack.root().resolve("content");
            if (!Files.exists(authoredRoot)) continue;
            if (!Files.isDirectory(authoredRoot)) {
                throw new ContentLoadException("Content path is not a directory: " + authoredRoot);
            }
            for (Path file : contentFiles(authoredRoot)) {
                for (RawItemDefinition definition : itemParser.parse(pack, file)) {
                    validateParentDependency(pack, definition);
                    Path previous = sources.put(definition.id(), file);
                    if (previous != null) {
                        throw new ContentLoadException("Duplicate item id " + definition.id()
                                + " in " + previous + " and " + file);
                    }
                    definitions.put(definition.id(), definition);
                }
            }
        }

        return Collections.unmodifiableList(new ArrayList<RawItemDefinition>(definitions.values()));
    }

    public ItemDefinitionRegistry load(Path contentRoot) {
        try {
            return compiler.compile(loadRaw(contentRoot));
        } catch (ContentCompileException exception) {
            throw new ContentLoadException("Content compilation failed: " + exception.getMessage(), exception);
        }
    }

    private static Map<String, ContentPack> indexAndValidatePacks(List<ContentPack> packs) {
        Map<String, ContentPack> indexed = new LinkedHashMap<String, ContentPack>();
        for (ContentPack pack : packs) {
            String namespace = pack.manifest().namespace();
            ContentPack previous = indexed.put(namespace, pack);
            if (previous != null) {
                throw new ContentLoadException("Duplicate content pack namespace '" + namespace + "' in "
                        + previous.root() + " and " + pack.root());
            }
        }
        for (ContentPack pack : packs) {
            for (String dependency : pack.manifest().dependencies()) {
                if (!indexed.containsKey(dependency)) {
                    throw new ContentLoadException("Content pack '" + pack.manifest().namespace()
                            + "' requires missing dependency '" + dependency + "'");
                }
            }
        }
        return indexed;
    }

    private static void validateParentDependency(ContentPack pack, RawItemDefinition definition) {
        ContentID parent = definition.parent();
        if (parent == null || parent.namespace().equals(pack.manifest().namespace())) return;
        if (!pack.manifest().dependsOn(parent.namespace())) {
            throw new ContentLoadException("Item " + definition.id() + " extends " + parent
                    + " but pack '" + pack.manifest().namespace() + "' does not declare dependency '"
                    + parent.namespace() + "'");
        }
    }

    private static List<Path> contentFiles(Path root) {
        List<Path> files = new ArrayList<Path>();
        try {
            java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(root);
            try {
                for (Path path : stream) {
                    if (Files.isDirectory(path)) {
                        files.addAll(contentFiles(path));
                    } else {
                        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                        if (name.endsWith(".yml") || name.endsWith(".yaml")) files.add(path);
                    }
                }
            } finally {
                stream.close();
            }
        } catch (IOException exception) {
            throw new ContentLoadException("Unable to discover content files under " + root, exception);
        }
        Collections.sort(files, new Comparator<Path>() {
            @Override
            public int compare(Path left, Path right) {
                return left.toString().compareTo(right.toString());
            }
        });
        return files;
    }
}
