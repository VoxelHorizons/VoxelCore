package org.voxelhorizons.pack;

import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.item.ItemRenderDefinition;
import org.voxelhorizons.content.load.ContentLoader;
import org.voxelhorizons.content.pack.ContentPack;
import org.voxelhorizons.content.pack.ContentPackDiscovery;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Deterministic Java resource-pack compiler for VoxelCore authored packs. */
public final class JavaPackCompiler {
    private static final int FIRST_AUTO_CMD = 1000;
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9._-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9/._-]+");

    private final ContentPackDiscovery packDiscovery;
    private final ContentLoader contentLoader;

    public JavaPackCompiler() {
        this(new ContentPackDiscovery(), new ContentLoader());
    }

    JavaPackCompiler(ContentPackDiscovery packDiscovery, ContentLoader contentLoader) {
        this.packDiscovery = packDiscovery;
        this.contentLoader = contentLoader;
    }

    public JavaPackBuildResult compile(Path contentRoot,
                                       Path outputZip,
                                       Path allocationManifestPath,
                                       JavaPackTarget target) {
        if (contentRoot == null || outputZip == null || allocationManifestPath == null || target == null) {
            throw new IllegalArgumentException("Compiler arguments cannot be null");
        }

        List<ContentPack> packs = packDiscovery.discover(contentRoot);
        Map<String, ContentPack> packsByNamespace = indexPacks(packs);
        ItemDefinitionRegistry registry = contentLoader.load(contentRoot);
        AllocationManifest allocations = loadAllocations(allocationManifestPath);
        TreeMap<String, byte[]> entries = new TreeMap<String, byte[]>();

        putEntry(entries, "pack.mcmeta", utf8(packMeta(target)));
        int copiedAssets = copyAuthoredAssets(packs, entries);

        List<ItemDefinition> items = new ArrayList<ItemDefinition>(registry.entries().values());
        Collections.sort(items, new Comparator<ItemDefinition>() {
            @Override
            public int compare(ItemDefinition left, ItemDefinition right) {
                return left.id().toString().compareTo(right.id().toString());
            }
        });

        Set<String> activeIds = new HashSet<String>();
        Set<Integer> reserved = allocations.reservedCustomModelData();
        Map<ResourceLocation, List<LegacyOverride>> legacyOverrides = new TreeMap<ResourceLocation, List<LegacyOverride>>();
        int renderedItems = 0;

        for (ItemDefinition item : items) {
            ItemRenderDefinition render = item.render();
            if (render == null) continue;
            if (render.model() == null || render.model().trim().isEmpty()) {
                if (render.legacyCustomModelData() != null) {
                    throw new JavaPackCompileException("Item " + item.id() + " defines Custom Model Data without render.model");
                }
                continue;
            }

            ContentPack itemPack = packsByNamespace.get(item.id().namespace());
            if (itemPack == null) {
                throw new JavaPackCompileException("No content pack owns item namespace " + item.id().namespace());
            }

            ResourceLocation model = ResourceLocation.parse(render.model(), item.id().namespace());
            validateModelReference(itemPack, packsByNamespace, model, item.id());
            validateModelAsset(packsByNamespace, model, item.id());

            Allocation allocation = allocations.reconcile(item.id(), model, render.legacyCustomModelData(), reserved);
            activeIds.add(item.id().toString());
            renderedItems++;

            if (target.mode() == JavaPackMode.ITEM_MODEL_1_21_4_PLUS) {
                String itemInfoPath = "assets/" + model.namespace + "/items/" + model.path + ".json";
                String json = "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \""
                        + json(model.toString()) + "\"\n  }\n}\n";
                putEntry(entries, itemInfoPath, utf8(json));
            } else {
                ResourceLocation material = ResourceLocation.parse(item.material(), "minecraft");
                List<LegacyOverride> overrides = legacyOverrides.get(material);
                if (overrides == null) {
                    overrides = new ArrayList<LegacyOverride>();
                    legacyOverrides.put(material, overrides);
                }
                overrides.add(new LegacyOverride(allocation.customModelData, model));
            }
        }

        allocations.markInactiveExcept(activeIds);

        if (target.mode() == JavaPackMode.NUMERIC_CUSTOM_MODEL_DATA) {
            for (Map.Entry<ResourceLocation, List<LegacyOverride>> entry : legacyOverrides.entrySet()) {
                Collections.sort(entry.getValue(), new Comparator<LegacyOverride>() {
                    @Override
                    public int compare(LegacyOverride left, LegacyOverride right) {
                        return Integer.compare(left.customModelData, right.customModelData);
                    }
                });
                ResourceLocation material = entry.getKey();
                String generatedPath = "assets/" + material.namespace + "/models/item/" + material.path + ".json";
                putEntry(entries, generatedPath, utf8(legacyModelJson(material, entry.getValue())));
            }
        }

        writeAllocationManifest(allocationManifestPath, allocations);
        writeDeterministicZip(outputZip, entries);
        return new JavaPackBuildResult(outputZip, allocationManifestPath, renderedItems, copiedAssets);
    }

    private static Map<String, ContentPack> indexPacks(List<ContentPack> packs) {
        Map<String, ContentPack> indexed = new LinkedHashMap<String, ContentPack>();
        for (ContentPack pack : packs) {
            ContentPack previous = indexed.put(pack.manifest().namespace(), pack);
            if (previous != null) {
                throw new JavaPackCompileException("Duplicate content pack namespace '" + pack.manifest().namespace() + "'");
            }
        }
        return indexed;
    }

    private static int copyAuthoredAssets(List<ContentPack> packs, Map<String, byte[]> entries) {
        int count = 0;
        for (ContentPack pack : packs) {
            Path assets = pack.root().resolve("assets");
            if (!Files.exists(assets)) continue;
            if (!Files.isDirectory(assets)) {
                throw new JavaPackCompileException("Assets path is not a directory: " + assets);
            }

            Path namespaceRoot = assets.resolve(pack.manifest().namespace());
            try {
                java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(assets);
                try {
                    for (Path child : stream) {
                        if (!child.getFileName().toString().equals(pack.manifest().namespace())) {
                            throw new JavaPackCompileException("Pack '" + pack.manifest().namespace()
                                    + "' may only author assets inside assets/" + pack.manifest().namespace()
                                    + "; found " + child.getFileName());
                        }
                    }
                } finally {
                    stream.close();
                }
            } catch (IOException exception) {
                throw new JavaPackCompileException("Unable to inspect assets for pack " + pack.manifest().namespace(), exception);
            }

            if (!Files.exists(namespaceRoot)) continue;
            List<Path> files = new ArrayList<Path>();
            collectFiles(namespaceRoot, files);
            Collections.sort(files, new Comparator<Path>() {
                @Override public int compare(Path left, Path right) { return left.toString().compareTo(right.toString()); }
            });
            for (Path file : files) {
                String relative = namespaceRoot.relativize(file).toString().replace('\\', '/');
                String zipPath = "assets/" + pack.manifest().namespace() + "/" + relative;
                try {
                    putEntry(entries, zipPath, Files.readAllBytes(file));
                } catch (IOException exception) {
                    throw new JavaPackCompileException("Unable to read authored asset " + file, exception);
                }
                count++;
            }
        }
        return count;
    }

    private static void collectFiles(Path root, List<Path> files) {
        try {
            java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(root);
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

    private static void validateModelReference(ContentPack itemPack,
                                               Map<String, ContentPack> packs,
                                               ResourceLocation model,
                                               ContentID itemId) {
        if (!packs.containsKey(model.namespace)) {
            throw new JavaPackCompileException("Item " + itemId + " references model namespace '"
                    + model.namespace + "' but no loaded pack owns that namespace");
        }
        if (!model.namespace.equals(itemPack.manifest().namespace())
                && !itemPack.manifest().dependsOn(model.namespace)) {
            throw new JavaPackCompileException("Item " + itemId + " references model " + model
                    + " but pack '" + itemPack.manifest().namespace()
                    + "' does not declare dependency '" + model.namespace + "'");
        }
    }

    private static void validateModelAsset(Map<String, ContentPack> packs, ResourceLocation model, ContentID itemId) {
        ContentPack owner = packs.get(model.namespace);
        Path expected = owner.root().resolve("assets").resolve(model.namespace)
                .resolve("models").resolve(model.path + ".json");
        if (!Files.isRegularFile(expected)) {
            throw new JavaPackCompileException("Item " + itemId + " references missing model " + model
                    + ". Expected " + expected);
        }
    }

    private static String packMeta(JavaPackTarget target) {
        return "{\n  \"pack\": {\n    \"pack_format\": " + target.packFormat()
                + ",\n    \"description\": \"VoxelCore generated pack (" + json(target.id()) + ")\"\n  }\n}\n";
    }

    private static String legacyModelJson(ResourceLocation material, List<LegacyOverride> overrides) {
        StringBuilder out = new StringBuilder();
        out.append("{\n  \"parent\": \"minecraft:item/generated\",\n");
        out.append("  \"textures\": {\"layer0\": \"").append(json(material.namespace + ":item/" + material.path)).append("\"},\n");
        out.append("  \"overrides\": [\n");
        for (int i = 0; i < overrides.size(); i++) {
            LegacyOverride override = overrides.get(i);
            out.append("    {\"predicate\": {\"custom_model_data\": ").append(override.customModelData)
                    .append("}, \"model\": \"").append(json(override.model.toString())).append("\"}");
            if (i + 1 < overrides.size()) out.append(',');
            out.append('\n');
        }
        out.append("  ]\n}\n");
        return out.toString();
    }

    private static AllocationManifest loadAllocations(Path path) {
        AllocationManifest manifest = new AllocationManifest();
        if (!Files.exists(path)) return manifest;
        Object loaded;
        try (InputStream input = Files.newInputStream(path)) {
            loaded = ContentPackDiscovery.yaml().load(input);
        } catch (IOException exception) {
            throw new JavaPackCompileException("Unable to read allocation manifest " + path, exception);
        }
        if (!(loaded instanceof Map)) throw new JavaPackCompileException("Allocation manifest must be a mapping: " + path);
        Map<?, ?> root = (Map<?, ?>) loaded;
        Object schema = root.get("schema");
        if (!(schema instanceof Number) || ((Number) schema).intValue() != 1) {
            throw new JavaPackCompileException("Unsupported allocation manifest schema in " + path);
        }
        Object next = root.get("next_legacy_custom_model_data");
        if (next instanceof Number) manifest.nextCustomModelData = Math.max(FIRST_AUTO_CMD, ((Number) next).intValue());
        Object allocations = root.get("allocations");
        if (allocations instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) allocations).entrySet()) {
                if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Map)) continue;
                String id = (String) entry.getKey();
                Map<?, ?> value = (Map<?, ?>) entry.getValue();
                Object cmd = value.get("legacy_custom_model_data");
                Object model = value.get("model");
                Object active = value.get("active");
                if (!(cmd instanceof Number) || !(model instanceof String)) {
                    throw new JavaPackCompileException("Invalid allocation entry for " + id + " in " + path);
                }
                manifest.allocations.put(id, new Allocation(((Number) cmd).intValue(), (String) model,
                        !(active instanceof Boolean) || ((Boolean) active).booleanValue()));
            }
        }
        return manifest;
    }

    private static void writeAllocationManifest(Path path, AllocationManifest manifest) {
        StringBuilder out = new StringBuilder();
        out.append("schema: 1\n");
        out.append("next_legacy_custom_model_data: ").append(manifest.nextCustomModelData).append("\n");
        out.append("allocations:\n");
        List<String> ids = new ArrayList<String>(manifest.allocations.keySet());
        Collections.sort(ids);
        for (String id : ids) {
            Allocation allocation = manifest.allocations.get(id);
            out.append("  '").append(yaml(id)).append("':\n");
            out.append("    legacy_custom_model_data: ").append(allocation.customModelData).append("\n");
            out.append("    model: '").append(yaml(allocation.model)).append("'\n");
            out.append("    active: ").append(allocation.active).append("\n");
        }
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.write(path, out.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new JavaPackCompileException("Unable to write allocation manifest " + path, exception);
        }
    }

    private static void writeDeterministicZip(Path output, TreeMap<String, byte[]> entries) {
        try {
            Path parent = output.getParent();
            if (parent != null) Files.createDirectories(parent);
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

    private static void putEntry(Map<String, byte[]> entries, String path, byte[] content) {
        byte[] previous = entries.put(path, content);
        if (previous != null && !java.util.Arrays.equals(previous, content)) {
            throw new JavaPackCompileException("Resource pack path collision: " + path);
        }
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String yaml(String value) {
        return value.replace("'", "''");
    }

    private static final class ResourceLocation implements Comparable<ResourceLocation> {
        private final String namespace;
        private final String path;

        private ResourceLocation(String namespace, String path) {
            this.namespace = namespace;
            this.path = path;
        }

        private static ResourceLocation parse(String input, String defaultNamespace) {
            if (input == null) throw new JavaPackCompileException("Resource location cannot be null");
            String value = input.trim().toLowerCase(java.util.Locale.ROOT);
            int colon = value.indexOf(':');
            String namespace = colon < 0 ? defaultNamespace : value.substring(0, colon);
            String path = colon < 0 ? value : value.substring(colon + 1);
            if (!NAMESPACE.matcher(namespace).matches() || !PATH.matcher(path).matches()
                    || path.startsWith("/") || path.endsWith("/") || path.contains("..")) {
                throw new JavaPackCompileException("Invalid resource location: " + input);
            }
            return new ResourceLocation(namespace, path);
        }

        @Override public String toString() { return namespace + ":" + path; }
        @Override public int compareTo(ResourceLocation other) { return toString().compareTo(other.toString()); }
        @Override public boolean equals(Object object) {
            if (!(object instanceof ResourceLocation)) return false;
            ResourceLocation other = (ResourceLocation) object;
            return namespace.equals(other.namespace) && path.equals(other.path);
        }
        @Override public int hashCode() { return 31 * namespace.hashCode() + path.hashCode(); }
    }

    private static final class LegacyOverride {
        private final int customModelData;
        private final ResourceLocation model;
        private LegacyOverride(int customModelData, ResourceLocation model) {
            this.customModelData = customModelData;
            this.model = model;
        }
    }

    private static final class Allocation {
        private final int customModelData;
        private String model;
        private boolean active;
        private Allocation(int customModelData, String model, boolean active) {
            this.customModelData = customModelData;
            this.model = model;
            this.active = active;
        }
    }

    private static final class AllocationManifest {
        private int nextCustomModelData = FIRST_AUTO_CMD;
        private final Map<String, Allocation> allocations = new HashMap<String, Allocation>();

        private Set<Integer> reservedCustomModelData() {
            Set<Integer> reserved = new HashSet<Integer>();
            for (Allocation allocation : allocations.values()) reserved.add(allocation.customModelData);
            return reserved;
        }

        private Allocation reconcile(ContentID id, ResourceLocation model, Integer explicit, Set<Integer> reserved) {
            String key = id.toString();
            Allocation existing = allocations.get(key);
            if (existing != null) {
                if (explicit != null && explicit.intValue() != existing.customModelData) {
                    throw new JavaPackCompileException("Item " + id + " requests legacy_custom_model_data " + explicit
                            + " but stable allocation manifest already reserves " + existing.customModelData);
                }
                existing.model = model.toString();
                existing.active = true;
                return existing;
            }

            int cmd;
            if (explicit != null) {
                if (explicit.intValue() < 0) throw new JavaPackCompileException("Custom Model Data cannot be negative for " + id);
                cmd = explicit.intValue();
                if (reserved.contains(cmd)) {
                    throw new JavaPackCompileException("Custom Model Data " + cmd + " is already reserved by another content ID");
                }
            } else {
                cmd = Math.max(FIRST_AUTO_CMD, nextCustomModelData);
                while (reserved.contains(cmd)) cmd++;
            }
            reserved.add(cmd);
            nextCustomModelData = Math.max(nextCustomModelData, cmd + 1);
            Allocation allocation = new Allocation(cmd, model.toString(), true);
            allocations.put(key, allocation);
            return allocation;
        }

        private void markInactiveExcept(Set<String> activeIds) {
            for (Map.Entry<String, Allocation> entry : allocations.entrySet()) {
                if (!activeIds.contains(entry.getKey())) entry.getValue().active = false;
            }
        }
    }
}
