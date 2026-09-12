package org.voxelhorizons.pack;

import org.bukkit.Material;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.CustomModelDataDefinition;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.item.ItemRenderDefinition;
import org.voxelhorizons.content.load.ContentLoader;
import org.voxelhorizons.content.pack.ContentPack;
import org.voxelhorizons.content.pack.ContentPackDiscovery;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public JavaPackCompiler() { this(new ContentPackDiscovery(), new ContentLoader()); }
    JavaPackCompiler(ContentPackDiscovery packDiscovery, ContentLoader contentLoader) {
        this.packDiscovery = packDiscovery;
        this.contentLoader = contentLoader;
    }

    public JavaPackBuildResult compile(Path contentRoot, Path outputZip, Path allocationManifestPath, JavaPackTarget target) {
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
            @Override public int compare(ItemDefinition left, ItemDefinition right) {
                return left.id().toString().compareTo(right.id().toString());
            }
        });

        Set<String> activeIds = new HashSet<String>();
        Set<Integer> reserved = allocations.reservedCustomModelData();
        Map<ResourceLocation, List<ModelOverride>> overrides = new TreeMap<ResourceLocation, List<ModelOverride>>();
        int renderedItems = 0;

        for (ItemDefinition item : items) {
            ItemRenderDefinition render = item.render();
            if (render == null) continue;
            if (render.model() == null || render.model().trim().isEmpty()) {
                if (render.customModelData() != null || render.durability() != null) {
                    throw new JavaPackCompileException("Item " + item.id() + " defines render predicates without render.model");
                }
                continue;
            }

            ContentPack itemPack = packsByNamespace.get(item.id().namespace());
            if (itemPack == null) throw new JavaPackCompileException("No content pack owns item namespace " + item.id().namespace());
            ResourceLocation model = ResourceLocation.parse(render.model(), item.id().namespace());
            validateModelReference(itemPack, packsByNamespace, model, item.id());
            validateModelAsset(packsByNamespace, model, item.id());

            CustomModelDataDefinition authored = render.customModelData();
            if (target.mode() == JavaPackMode.NUMERIC_CUSTOM_MODEL_DATA && authored != null && authored.isStructured()) {
                throw new JavaPackCompileException("Structured custom_model_data requires a 1.21.4+ Java pack target for " + item.id());
            }
            Integer explicit = authored != null && authored.isNumeric() ? authored.numeric() : null;
            Allocation allocation = allocations.reconcile(item.id(), model, explicit, reserved);
            activeIds.add(item.id().toString());
            renderedItems++;

            if (target.mode() == JavaPackMode.ITEM_MODEL_1_21_4_PLUS) {
                String itemInfoPath = "assets/" + model.namespace + "/items/" + model.path + ".json";
                String itemInfo = "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \""
                        + json(model.toString()) + "\"\n  }\n}\n";
                putEntry(entries, itemInfoPath, utf8(itemInfo));
            } else {
                Integer predicateValue = target.mode() == JavaPackMode.LEGACY_DAMAGE_UNBREAKABLE
                        ? render.durability()
                        : Integer.valueOf(allocation.customModelData);
                if (predicateValue != null) {
                    ResourceLocation material = ResourceLocation.parse(item.material(), "minecraft");
                    List<ModelOverride> materialOverrides = overrides.get(material);
                    if (materialOverrides == null) {
                        materialOverrides = new ArrayList<ModelOverride>();
                        overrides.put(material, materialOverrides);
                    }
                    materialOverrides.add(new ModelOverride(predicateValue.intValue(), model));
                }
            }
        }

        allocations.markInactiveExcept(activeIds);
        if (target.mode() == JavaPackMode.NUMERIC_CUSTOM_MODEL_DATA) {
            writeNumericOverrides(entries, overrides);
        } else if (target.mode() == JavaPackMode.LEGACY_DAMAGE_UNBREAKABLE) {
            writeLegacyDamageOverrides(entries, overrides);
        }

        writeAllocationManifest(allocationManifestPath, allocations);
        writeDeterministicZip(outputZip, entries);
        return new JavaPackBuildResult(outputZip, allocationManifestPath, renderedItems, copiedAssets);
    }

    private static void writeNumericOverrides(Map<String, byte[]> entries, Map<ResourceLocation, List<ModelOverride>> overrides) {
        for (Map.Entry<ResourceLocation, List<ModelOverride>> entry : overrides.entrySet()) {
            Collections.sort(entry.getValue(), byValue());
            ResourceLocation material = entry.getKey();
            String path = "assets/" + material.namespace + "/models/item/" + material.path + ".json";
            putEntry(entries, path, utf8(numericModelJson(material, entry.getValue())));
        }
    }

    private static void writeLegacyDamageOverrides(Map<String, byte[]> entries, Map<ResourceLocation, List<ModelOverride>> overrides) {
        for (Map.Entry<ResourceLocation, List<ModelOverride>> entry : overrides.entrySet()) {
            ResourceLocation material = entry.getKey();
            if (!"minecraft".equals(material.namespace)) {
                throw new JavaPackCompileException("Legacy damage rendering requires a minecraft base material: " + material);
            }
            Material bukkit = Material.matchMaterial(material.path.toUpperCase(java.util.Locale.ROOT));
            if (bukkit == null || bukkit.getMaxDurability() <= 0) {
                throw new JavaPackCompileException("Legacy damage rendering requires a damageable 1.12 material: " + material);
            }
            int max = bukkit.getMaxDurability();
            Collections.sort(entry.getValue(), byValue());
            for (ModelOverride override : entry.getValue()) {
                if (override.value < 0 || override.value >= max) {
                    throw new JavaPackCompileException("durability " + override.value + " cannot be represented by "
                            + material + " on 1.12; expected 0.." + (max - 1));
                }
            }
            String path = "assets/minecraft/models/item/" + material.path + ".json";
            putEntry(entries, path, utf8(legacyDamageModelJson(material, max, entry.getValue())));
        }
    }

    private static Comparator<ModelOverride> byValue() {
        return new Comparator<ModelOverride>() {
            @Override public int compare(ModelOverride left, ModelOverride right) { return Integer.compare(left.value, right.value); }
        };
    }

    private static Map<String, ContentPack> indexPacks(List<ContentPack> packs) {
        Map<String, ContentPack> indexed = new LinkedHashMap<String, ContentPack>();
        for (ContentPack pack : packs) {
            ContentPack previous = indexed.put(pack.manifest().namespace(), pack);
            if (previous != null) throw new JavaPackCompileException("Duplicate content pack namespace '" + pack.manifest().namespace() + "'");
        }
        return indexed;
    }

    private static int copyAuthoredAssets(List<ContentPack> packs, Map<String, byte[]> entries) {
        int count = 0;
        for (ContentPack pack : packs) {
            Path assets = pack.root().resolve("assets");
            if (!Files.exists(assets)) continue;
            if (!Files.isDirectory(assets)) throw new JavaPackCompileException("Assets path is not a directory: " + assets);
            Path namespaceRoot = assets.resolve(pack.manifest().namespace());
            try {
                java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(assets);
                try {
                    for (Path child : stream) {
                        if (!child.getFileName().toString().equals(pack.manifest().namespace())) {
                            throw new JavaPackCompileException("Pack '" + pack.manifest().namespace()
                                    + "' may only author assets inside assets/" + pack.manifest().namespace() + "; found " + child.getFileName());
                        }
                    }
                } finally { stream.close(); }
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
                try { putEntry(entries, "assets/" + pack.manifest().namespace() + "/" + relative, Files.readAllBytes(file)); }
                catch (IOException exception) { throw new JavaPackCompileException("Unable to read authored asset " + file, exception); }
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
                    if (Files.isSymbolicLink(path)) throw new JavaPackCompileException("Symbolic links are not supported in authored assets: " + path);
                    if (Files.isDirectory(path)) collectFiles(path, files);
                    else if (Files.isRegularFile(path)) files.add(path);
                }
            } finally { stream.close(); }
        } catch (IOException exception) { throw new JavaPackCompileException("Unable to discover assets under " + root, exception); }
    }

    private static void validateModelReference(ContentPack itemPack, Map<String, ContentPack> packs, ResourceLocation model, ContentID itemId) {
        if (!packs.containsKey(model.namespace)) {
            throw new JavaPackCompileException("Item " + itemId + " references model namespace '" + model.namespace + "' but no loaded pack owns that namespace");
        }
        if (!model.namespace.equals(itemPack.manifest().namespace()) && !itemPack.manifest().dependsOn(model.namespace)) {
            throw new JavaPackCompileException("Item " + itemId + " references model " + model + " but pack '"
                    + itemPack.manifest().namespace() + "' does not declare dependency '" + model.namespace + "'");
        }
    }

    private static void validateModelAsset(Map<String, ContentPack> packs, ResourceLocation model, ContentID itemId) {
        ContentPack owner = packs.get(model.namespace);
        Path expected = owner.root().resolve("assets").resolve(model.namespace).resolve("models").resolve(model.path + ".json");
        if (!Files.isRegularFile(expected)) throw new JavaPackCompileException("Item " + itemId + " references missing model " + model + ". Expected " + expected);
    }

    private static String packMeta(JavaPackTarget target) {
        return "{\n  \"pack\": {\n    \"pack_format\": " + target.packFormat()
                + ",\n    \"description\": \"VoxelCore generated pack (" + json(target.id()) + ")\"\n  }\n}\n";
    }

    private static String numericModelJson(ResourceLocation material, List<ModelOverride> overrides) {
        StringBuilder out = new StringBuilder();
        out.append("{\n  \"parent\": \"minecraft:item/generated\",\n");
        out.append("  \"textures\": {\"layer0\": \"").append(json(material.namespace + ":item/" + material.path)).append("\"},\n");
        out.append("  \"overrides\": [\n");
        for (int i = 0; i < overrides.size(); i++) {
            ModelOverride override = overrides.get(i);
            out.append("    {\"predicate\": {\"custom_model_data\": ").append(override.value)
                    .append("}, \"model\": \"").append(json(override.model.toString())).append("\"}");
            if (i + 1 < overrides.size()) out.append(',');
            out.append('\n');
        }
        out.append("  ]\n}\n");
        return out.toString();
    }

    private static String legacyDamageModelJson(ResourceLocation material, int maxDurability, List<ModelOverride> overrides) {
        StringBuilder out = new StringBuilder();
        out.append("{\n  \"parent\": \"minecraft:item/handheld\",\n");
        out.append("  \"textures\": {\"layer0\": \"minecraft:items/").append(json(material.path)).append("\"},\n");
        out.append("  \"overrides\": [\n");
        for (int i = 0; i < overrides.size(); i++) {
            ModelOverride override = overrides.get(i);
            String damage = BigDecimal.valueOf((double) override.value / (double) maxDurability)
                    .setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            out.append("    {\"predicate\": {\"damaged\": 0, \"damage\": ").append(damage)
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
        try (InputStream input = Files.newInputStream(path)) { loaded = ContentPackDiscovery.yaml().load(input); }
        catch (IOException exception) { throw new JavaPackCompileException("Unable to read allocation manifest " + path, exception); }
        if (!(loaded instanceof Map)) throw new JavaPackCompileException("Allocation manifest must be a mapping: " + path);
        Map<?, ?> root = (Map<?, ?>) loaded;
        Object schema = root.get("schema");
        if (!(schema instanceof Number) || ((Number) schema).intValue() != 2) {
            throw new JavaPackCompileException("Unsupported allocation manifest schema in " + path + "; expected 2");
        }
        Object next = root.get("next_custom_model_data");
        if (next instanceof Number) manifest.nextCustomModelData = Math.max(FIRST_AUTO_CMD, ((Number) next).intValue());
        Object allocations = root.get("allocations");
        if (allocations instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) allocations).entrySet()) {
                if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Map)) continue;
                String id = (String) entry.getKey();
                Map<?, ?> value = (Map<?, ?>) entry.getValue();
                Object cmd = value.get("custom_model_data");
                Object model = value.get("model");
                Object active = value.get("active");
                if (!(cmd instanceof Number) || !(model instanceof String)) throw new JavaPackCompileException("Invalid allocation entry for " + id + " in " + path);
                manifest.allocations.put(id, new Allocation(((Number) cmd).intValue(), (String) model,
                        !(active instanceof Boolean) || ((Boolean) active).booleanValue()));
            }
        }
        return manifest;
    }

    private static void writeAllocationManifest(Path path, AllocationManifest manifest) {
        StringBuilder out = new StringBuilder();
        out.append("schema: 2\n");
        out.append("next_custom_model_data: ").append(manifest.nextCustomModelData).append("\n");
        out.append("allocations:\n");
        List<String> ids = new ArrayList<String>(manifest.allocations.keySet());
        Collections.sort(ids);
        for (String id : ids) {
            Allocation allocation = manifest.allocations.get(id);
            out.append("  '").append(yaml(id)).append("':\n");
            out.append("    custom_model_data: ").append(allocation.customModelData).append("\n");
            out.append("    model: '").append(yaml(allocation.model)).append("'\n");
            out.append("    active: ").append(allocation.active).append("\n");
        }
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.write(path, out.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException exception) { throw new JavaPackCompileException("Unable to write allocation manifest " + path, exception); }
    }

    private static void writeDeterministicZip(Path output, TreeMap<String, byte[]> entries) {
        try {
            Path parent = output.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (OutputStream raw = Files.newOutputStream(output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                 ZipOutputStream zip = new ZipOutputStream(raw)) {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    byte[] bytes = entry.getValue();
                    CRC32 crc = new CRC32(); crc.update(bytes);
                    ZipEntry zipEntry = new ZipEntry(entry.getKey());
                    zipEntry.setMethod(ZipEntry.STORED); zipEntry.setSize(bytes.length); zipEntry.setCompressedSize(bytes.length);
                    zipEntry.setCrc(crc.getValue()); zipEntry.setTime(0L);
                    zip.putNextEntry(zipEntry); zip.write(bytes); zip.closeEntry();
                }
            }
        } catch (IOException exception) { throw new JavaPackCompileException("Unable to write generated resource pack " + output, exception); }
    }

    private static void putEntry(Map<String, byte[]> entries, String path, byte[] content) {
        byte[] previous = entries.put(path, content);
        if (previous != null && !java.util.Arrays.equals(previous, content)) throw new JavaPackCompileException("Resource pack path collision: " + path);
    }
    private static byte[] utf8(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String yaml(String value) { return value.replace("'", "''"); }

    private static final class ResourceLocation implements Comparable<ResourceLocation> {
        private final String namespace;
        private final String path;
        private ResourceLocation(String namespace, String path) { this.namespace = namespace; this.path = path; }
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

    private static final class ModelOverride {
        private final int value;
        private final ResourceLocation model;
        private ModelOverride(int value, ResourceLocation model) { this.value = value; this.model = model; }
    }

    private static final class Allocation {
        private final int customModelData;
        private String model;
        private boolean active;
        private Allocation(int customModelData, String model, boolean active) {
            this.customModelData = customModelData; this.model = model; this.active = active;
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
                    throw new JavaPackCompileException("Item " + id + " requests custom_model_data " + explicit
                            + " but stable allocation manifest already reserves " + existing.customModelData);
                }
                existing.model = model.toString(); existing.active = true; return existing;
            }
            int cmd;
            if (explicit != null) {
                if (explicit.intValue() < 0) throw new JavaPackCompileException("Custom model data cannot be negative for " + id);
                cmd = explicit.intValue();
                if (reserved.contains(cmd)) throw new JavaPackCompileException("Custom model data " + cmd + " is already reserved by another content ID");
            } else {
                cmd = Math.max(FIRST_AUTO_CMD, nextCustomModelData);
                while (reserved.contains(cmd)) cmd++;
            }
            reserved.add(cmd); nextCustomModelData = Math.max(nextCustomModelData, cmd + 1);
            Allocation allocation = new Allocation(cmd, model.toString(), true); allocations.put(key, allocation); return allocation;
        }
        private void markInactiveExcept(Set<String> activeIds) {
            for (Map.Entry<String, Allocation> entry : allocations.entrySet()) if (!activeIds.contains(entry.getKey())) entry.getValue().active = false;
        }
    }
}
