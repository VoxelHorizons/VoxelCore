package org.voxelhorizons.content.pack;

import org.voxelhorizons.content.load.ContentLoadException;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ContentPackDiscovery {

    public List<ContentPack> discover(Path contentRoot) {
        if (contentRoot == null) {
            throw new ContentLoadException("Content root cannot be null");
        }
        if (!Files.exists(contentRoot)) {
            return Collections.emptyList();
        }
        if (!Files.isDirectory(contentRoot)) {
            throw new ContentLoadException("Content root is not a directory: " + contentRoot);
        }

        List<Path> directories = new ArrayList<Path>();
        try {
            java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(contentRoot);
            try {
                for (Path path : stream) {
                    if (Files.isDirectory(path)) directories.add(path);
                }
            } finally {
                stream.close();
            }
        } catch (IOException exception) {
            throw new ContentLoadException("Unable to discover content packs in " + contentRoot, exception);
        }

        Collections.sort(directories, new Comparator<Path>() {
            @Override
            public int compare(Path left, Path right) {
                return left.getFileName().toString().compareTo(right.getFileName().toString());
            }
        });

        List<ContentPack> packs = new ArrayList<ContentPack>();
        for (Path directory : directories) {
            packs.add(new ContentPack(directory, readManifest(directory.resolve("pack.yml"))));
        }
        return Collections.unmodifiableList(packs);
    }

    private ContentPackManifest readManifest(Path manifestPath) {
        if (!Files.isRegularFile(manifestPath)) {
            throw new ContentLoadException("Missing content pack manifest: " + manifestPath);
        }

        Object loaded;
        try (InputStream input = Files.newInputStream(manifestPath)) {
            loaded = yaml().load(input);
        } catch (IOException exception) {
            throw new ContentLoadException("Unable to read content pack manifest: " + manifestPath, exception);
        } catch (RuntimeException exception) {
            throw new ContentLoadException("Invalid YAML in content pack manifest: " + manifestPath, exception);
        }

        if (!(loaded instanceof Map)) {
            throw new ContentLoadException("Content pack manifest must be a mapping: " + manifestPath);
        }

        Map<?, ?> values = (Map<?, ?>) loaded;
        rejectUnknown(values, manifestPath, "schema", "namespace");

        Object schemaValue = values.get("schema");
        Object namespaceValue = values.get("namespace");
        if (!(schemaValue instanceof Number)) {
            throw new ContentLoadException("pack.yml schema must be an integer: " + manifestPath);
        }
        if (!(namespaceValue instanceof String) || ((String) namespaceValue).trim().isEmpty()) {
            throw new ContentLoadException("pack.yml namespace must be a non-empty string: " + manifestPath);
        }

        try {
            return new ContentPackManifest(((Number) schemaValue).intValue(), (String) namespaceValue);
        } catch (IllegalArgumentException exception) {
            throw new ContentLoadException("Invalid content pack manifest " + manifestPath + ": " + exception.getMessage(), exception);
        }
    }

    private static void rejectUnknown(Map<?, ?> values, Path source, String... allowed) {
        Set<String> allowedKeys = new HashSet<String>();
        Collections.addAll(allowedKeys, allowed);
        for (Object key : values.keySet()) {
            if (!(key instanceof String) || !allowedKeys.contains(key)) {
                throw new ContentLoadException("Unsupported key '" + key + "' in " + source);
            }
        }
    }

    public static Yaml yaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(25);
        options.setCodePointLimit(3 * 1024 * 1024);
        return new Yaml(new SafeConstructor(options));
    }
}
