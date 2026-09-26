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
import org.voxelhorizons.content.render.RenderAllocation;
import org.voxelhorizons.content.render.RenderAllocationRegistry;
import org.voxelhorizons.content.render.RenderAllocationStore;
import org.voxelhorizons.content.render.StructuredModelDataAllocation;
import org.voxelhorizons.content.block.BlockAllocationRegistry;
import org.voxelhorizons.content.block.BlockAllocationStore;
import org.voxelhorizons.content.block.BlockDefinitionRegistry;
import org.voxelhorizons.content.block.BlockDefinition;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Internal deterministic Java pack compiler using the common render allocation authority. */
final class JavaPackCompilerEngine {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9._-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9/._-]+");

    private final ContentPackDiscovery packDiscovery;
    private final ContentLoader contentLoader;

    JavaPackCompilerEngine(ContentPackDiscovery packDiscovery, ContentLoader contentLoader) {
        this.packDiscovery = packDiscovery;
        this.contentLoader = contentLoader;
    }

    JavaPackBuildResult compile(Path contentRoot, Path outputZip, Path allocationManifestPath,
                                JavaPackTarget target, boolean writeOutput) {
        if (contentRoot == null || allocationManifestPath == null || target == null) {
            throw new IllegalArgumentException("Compiler arguments cannot be null");
        }
        if (writeOutput && outputZip == null) throw new IllegalArgumentException("outputZip cannot be null when building");

        List<ContentPack> packs = packDiscovery.discover(contentRoot);
        Map<String, ContentPack> packsByNamespace = indexPacks(packs);
        ItemDefinitionRegistry registry = contentLoader.load(contentRoot);
        BlockDefinitionRegistry blocks = contentLoader.loadBlocks(contentRoot);
        Path glyphAllocationPath = allocationManifestPath.resolveSibling("glyph-allocations.yml");
        UiGlyphLoader glyphLoader = new UiGlyphLoader();
        UiGlyphRegistry glyphs = glyphLoader.load(contentRoot, glyphAllocationPath, false);
        if (!target.supportsUiFonts() && glyphs.size() > 0) {
            throw new JavaPackCompileException("Java pack target " + target.id()
                    + " does not support custom UI fonts");
        }

        RenderAllocationStore allocationStore = new RenderAllocationStore(allocationManifestPath);
        RenderAllocationRegistry allocations;
        try {
            allocations = RenderAllocationRegistry.reconcile(registry, allocationStore.load());
        } catch (RuntimeException exception) {
            throw new JavaPackCompileException("Unable to reconcile render allocations: " + exception.getMessage(), exception);
        }

        TreeMap<String, byte[]> entries = new TreeMap<String, byte[]>();
        putEntry(entries, "pack.mcmeta", utf8(packMeta(target)));
        int copiedAssets = copyAuthoredAssets(packs, entries);
        writeCustomTextureAtlas(packs, target, entries);
        if (target.supportsUiFonts()) {
            writeTooltipResources(entries);
            writeContainerGuiResources(entries);
            writeUiFont(entries, glyphs);
        }

        List<ItemDefinition> items = new ArrayList<ItemDefinition>(registry.entries().values());
        Collections.sort(items, new Comparator<ItemDefinition>() {
            @Override public int compare(ItemDefinition left, ItemDefinition right) {
                return left.id().toString().compareTo(right.id().toString());
            }
        });

        Map<ResourceLocation, List<ModelOverride>> overrides = new TreeMap<ResourceLocation, List<ModelOverride>>();
        int renderedItems = 0;

        for (ItemDefinition item : items) {
            // Abstract variants may have authored models used by addons; they remain non-giveable.
            if (item.abstractDefinition() && (item.render() == null || item.render().model() == null)) continue;
            ItemRenderDefinition render = item.render();
            if (render == null) continue;
            if (render.model() == null || render.model().trim().isEmpty()) {
                if (render.customModelData() != null || render.durability() != null || !render.rule().isEmpty()) {
                    throw new JavaPackCompileException("Item " + item.id() + " defines render state without render.model");
                }
                continue;
            }
            if (item.abstractDefinition() && (item.material() == null || item.material().trim().isEmpty())) {
                throw new JavaPackCompileException("Abstract render model requires a material for " + item.id());
            }

            ContentPack itemPack = packsByNamespace.get(item.id().namespace());
            if (itemPack == null) throw new JavaPackCompileException("No content pack owns item namespace " + item.id().namespace());
            ResourceLocation model = ResourceLocation.parse(render.model(), item.id().namespace());
            validateModelReference(itemPack, packsByNamespace, model, item.id());
            if (!isGeneratedBlockModel(model, item.id(), blocks)) {
                validateModelAsset(packsByNamespace, model, item.id());
            }

            CustomModelDataDefinition authored = render.customModelData();
            if (target.mode() == JavaPackMode.NUMERIC_CUSTOM_MODEL_DATA && authored != null && authored.isStructured()) {
                throw new JavaPackCompileException("Structured custom_model_data requires a 1.21.4+ Java pack target for " + item.id());
            }
            if (target.mode() != JavaPackMode.ITEM_MODEL_1_21_4_PLUS && !render.rule().isEmpty()) {
                throw new JavaPackCompileException("render.rule requires a 1.21.4+ Java pack target for " + item.id());
            }

            RenderAllocation allocation = allocations.get(item.id()).orElseThrow(
                    () -> new JavaPackCompileException("Missing render allocation for " + item.id()));
            renderedItems++;

            if (target.mode() == JavaPackMode.ITEM_MODEL_1_21_4_PLUS) {
                String itemInfoPath = "assets/" + model.namespace + "/items/" + model.path + ".json";
                String node = render.rule().isEmpty()
                        ? modelNode(model, null, allocation)
                        : compileModernNode(render.rule(), item, itemPack, packsByNamespace, allocation);
                putEntry(entries, itemInfoPath, utf8("{\n  \"model\": " + node + "\n}\n"));
            } else {
                Integer predicateValue = target.mode() == JavaPackMode.LEGACY_DAMAGE_UNBREAKABLE
                        ? legacyPredicateValue(item, render, model, blocks, allocation)
                        : Integer.valueOf(allocation.customModelData());
                if (predicateValue != null) {
                    ResourceLocation material = target.mode() == JavaPackMode.LEGACY_DAMAGE_UNBREAKABLE
                            && isGeneratedBlockModel(model, item.id(), blocks)
                            ? ResourceLocation.parse("minecraft:diamond_hoe", "minecraft")
                            : ResourceLocation.parse(item.material(), "minecraft");
                    List<ModelOverride> materialOverrides = overrides.get(material);
                    if (materialOverrides == null) {
                        materialOverrides = new ArrayList<ModelOverride>();
                        overrides.put(material, materialOverrides);
                    }
                    addOverride(materialOverrides, predicateValue.intValue(), model, item.id());
                }
            }
        }

        if (target.mode() == JavaPackMode.NUMERIC_CUSTOM_MODEL_DATA) {
            writeNumericOverrides(entries, overrides);
        } else if (target.mode() == JavaPackMode.LEGACY_DAMAGE_UNBREAKABLE) {
            writeLegacyDamageOverrides(entries, overrides);
        }

        boolean modernBlockStates = target.supportsFlattenedBlockStates();
        BlockAllocationStore blockAllocationStore = new BlockAllocationStore(
                allocationManifestPath.resolveSibling("block-allocations.yml"));
        BlockAllocationRegistry blockAllocations = BlockAllocationRegistry.reconcile(
                blocks, blockAllocationStore.load(), modernBlockStates);
        validateBlockTextures(blocks, packsByNamespace);
        int renderedBlocks = BlockPackCompiler.write(entries, blocks, blockAllocations, modernBlockStates);

        if (writeOutput) {
            try {
                allocationStore.save(allocations);
            } catch (RuntimeException exception) {
                throw new JavaPackCompileException("Unable to persist render allocations: " + exception.getMessage(), exception);
            }
            glyphLoader.load(contentRoot, glyphAllocationPath, true);
            blockAllocationStore.save(blockAllocations);
            writeDeterministicZip(outputZip, entries);
        }
        return new JavaPackBuildResult(outputZip, allocationManifestPath, renderedItems, copiedAssets, renderedBlocks);
    }

    private static void validateBlockTextures(BlockDefinitionRegistry blocks, Map<String, ContentPack> packs) {
        for (BlockDefinition definition : blocks.entries().values()) {
            if (definition.abstractDefinition()) continue;
            for (String texture : definition.textures().values()) {
                int separator = texture.indexOf(':');
                String namespace = texture.substring(0, separator);
                String path = texture.substring(separator + 1);
                if ("minecraft".equals(namespace)) continue;
                ContentPack owner = packs.get(namespace);
                if (owner == null) throw new JavaPackCompileException("Block " + definition.id()
                        + " references texture in missing namespace " + namespace);
                Path file = owner.root().resolve("assets").resolve(namespace).resolve("textures").resolve(path + ".png");
                if (!Files.isRegularFile(file)) throw new JavaPackCompileException("Block " + definition.id()
                        + " references missing texture. Expected " + file);
            }
        }
    }

    private static String compileModernNode(Object raw,
                                            ItemDefinition item,
                                            ContentPack itemPack,
                                            Map<String, ContentPack> packs,
                                            RenderAllocation allocation) {
        if (raw instanceof String) {
            ResourceLocation model = validateRuleModel((String) raw, item, itemPack, packs);
            return modelNode(model, null, allocation);
        }
        if (!(raw instanceof Map)) throw new JavaPackCompileException("Render rule node must be a model string or mapping for " + item.id());
        Map<?, ?> node = (Map<?, ?>) raw;
        int kinds = (node.containsKey("model") ? 1 : 0) + (node.containsKey("select") ? 1 : 0)
                + (node.containsKey("condition") ? 1 : 0) + (node.containsKey("range") ? 1 : 0);
        if (kinds != 1 || node.size() != 1) {
            throw new JavaPackCompileException("Render rule node for " + item.id()
                    + " must contain exactly one of model, select, condition or range");
        }
        if (node.containsKey("model")) return compileModelRule(node.get("model"), item, itemPack, packs, allocation);
        if (node.containsKey("select")) return compileSelectRule(node.get("select"), item, itemPack, packs, allocation);
        if (node.containsKey("condition")) return compileConditionRule(node.get("condition"), item, itemPack, packs, allocation);
        return compileRangeRule(node.get("range"), item, itemPack, packs, allocation);
    }

    private static String compileModelRule(Object raw,
                                           ItemDefinition item,
                                           ContentPack itemPack,
                                           Map<String, ContentPack> packs,
                                           RenderAllocation allocation) {
        if (raw instanceof String) {
            return modelNode(validateRuleModel((String) raw, item, itemPack, packs), null, allocation);
        }
        if (!(raw instanceof Map)) throw new JavaPackCompileException("render.rule.model must be a string or mapping for " + item.id());
        Map<?, ?> modelRule = (Map<?, ?>) raw;
        Object id = modelRule.get("id");
        if (!(id instanceof String)) throw new JavaPackCompileException("render.rule.model.id must be a string for " + item.id());
        for (Object key : modelRule.keySet()) {
            if (!"id".equals(key) && !"tint".equals(key)) throw new JavaPackCompileException("Unsupported render.rule.model key '" + key + "' for " + item.id());
        }
        String tint = null;
        if (modelRule.containsKey("tint")) {
            if (!(modelRule.get("tint") instanceof String)) throw new JavaPackCompileException("render.rule.model.tint must be a string for " + item.id());
            tint = (String) modelRule.get("tint");
            requireIndex(allocation, CustomModelDataDefinition.ValueType.COLOR, tint, item.id());
        }
        return modelNode(validateRuleModel((String) id, item, itemPack, packs), tint, allocation);
    }

    private static String compileSelectRule(Object raw,
                                            ItemDefinition item,
                                            ContentPack itemPack,
                                            Map<String, ContentPack> packs,
                                            RenderAllocation allocation) {
        Map<?, ?> rule = requireMap(raw, "select", item.id());
        requireOnly(rule, item.id(), "select", "key", "cases", "fallback");
        String key = requireString(rule.get("key"), "render.rule.select.key", item.id());
        int index = requireIndex(allocation, CustomModelDataDefinition.ValueType.STRING, key, item.id());
        Map<?, ?> cases = requireMap(rule.get("cases"), "select.cases", item.id());
        if (cases.isEmpty()) throw new JavaPackCompileException("render.rule.select.cases cannot be empty for " + item.id());
        List<String> caseKeys = stringKeys(cases, "render.rule.select.cases", item.id());
        Collections.sort(caseKeys);
        StringBuilder out = new StringBuilder();
        out.append("{\"type\":\"minecraft:select\",\"property\":\"minecraft:custom_model_data\",\"index\":")
                .append(index).append(",\"cases\":[");
        for (int i = 0; i < caseKeys.size(); i++) {
            String value = caseKeys.get(i);
            if (i > 0) out.append(',');
            out.append("{\"when\":\"").append(json(value)).append("\",\"model\":")
                    .append(compileModernNode(cases.get(value), item, itemPack, packs, allocation)).append('}');
        }
        out.append(']');
        if (rule.containsKey("fallback")) out.append(",\"fallback\":")
                .append(compileModernNode(rule.get("fallback"), item, itemPack, packs, allocation));
        out.append('}');
        return out.toString();
    }

    private static String compileConditionRule(Object raw,
                                               ItemDefinition item,
                                               ContentPack itemPack,
                                               Map<String, ContentPack> packs,
                                               RenderAllocation allocation) {
        Map<?, ?> rule = requireMap(raw, "condition", item.id());
        requireOnly(rule, item.id(), "condition", "key", "true", "false");
        String key = requireString(rule.get("key"), "render.rule.condition.key", item.id());
        int index = requireIndex(allocation, CustomModelDataDefinition.ValueType.FLAG, key, item.id());
        if (!rule.containsKey("true") || !rule.containsKey("false")) {
            throw new JavaPackCompileException("render.rule.condition requires true and false branches for " + item.id());
        }
        return "{\"type\":\"minecraft:condition\",\"property\":\"minecraft:custom_model_data\",\"index\":" + index
                + ",\"on_true\":" + compileModernNode(rule.get("true"), item, itemPack, packs, allocation)
                + ",\"on_false\":" + compileModernNode(rule.get("false"), item, itemPack, packs, allocation) + "}";
    }

    private static String compileRangeRule(Object raw,
                                           ItemDefinition item,
                                           ContentPack itemPack,
                                           Map<String, ContentPack> packs,
                                           RenderAllocation allocation) {
        Map<?, ?> rule = requireMap(raw, "range", item.id());
        requireOnly(rule, item.id(), "range", "key", "entries", "fallback");
        String key = requireString(rule.get("key"), "render.rule.range.key", item.id());
        int index = requireIndex(allocation, CustomModelDataDefinition.ValueType.FLOAT, key, item.id());
        Map<?, ?> entries = requireMap(rule.get("entries"), "range.entries", item.id());
        if (entries.isEmpty()) throw new JavaPackCompileException("render.rule.range.entries cannot be empty for " + item.id());
        List<RangeCase> cases = new ArrayList<RangeCase>();
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            double threshold;
            try { threshold = Double.parseDouble(String.valueOf(entry.getKey())); }
            catch (NumberFormatException exception) { throw new JavaPackCompileException("Range threshold '" + entry.getKey() + "' is not numeric for " + item.id()); }
            if (Double.isNaN(threshold) || Double.isInfinite(threshold)) throw new JavaPackCompileException("Range threshold must be finite for " + item.id());
            cases.add(new RangeCase(threshold, entry.getValue()));
        }
        Collections.sort(cases, new Comparator<RangeCase>() {
            @Override public int compare(RangeCase left, RangeCase right) { return Double.compare(left.threshold, right.threshold); }
        });
        StringBuilder out = new StringBuilder();
        out.append("{\"type\":\"minecraft:range_dispatch\",\"property\":\"minecraft:custom_model_data\",\"index\":")
                .append(index).append(",\"entries\":[");
        for (int i = 0; i < cases.size(); i++) {
            if (i > 0) out.append(',');
            RangeCase rangeCase = cases.get(i);
            out.append("{\"threshold\":").append(BigDecimal.valueOf(rangeCase.threshold).stripTrailingZeros().toPlainString())
                    .append(",\"model\":").append(compileModernNode(rangeCase.node, item, itemPack, packs, allocation)).append('}');
        }
        out.append(']');
        if (rule.containsKey("fallback")) out.append(",\"fallback\":")
                .append(compileModernNode(rule.get("fallback"), item, itemPack, packs, allocation));
        out.append('}');
        return out.toString();
    }

    private static String modelNode(ResourceLocation model, String tintKey, RenderAllocation allocation) {
        StringBuilder out = new StringBuilder("{\"type\":\"minecraft:model\",\"model\":\"")
                .append(json(model.toString())).append("\"");
        if (tintKey != null) {
            int index = requireIndex(allocation, CustomModelDataDefinition.ValueType.COLOR, tintKey, null);
            out.append(",\"tints\":[{\"type\":\"minecraft:custom_model_data\",\"index\":")
                    .append(index).append(",\"default\":-1}]");
        }
        return out.append('}').toString();
    }

    private static ResourceLocation validateRuleModel(String raw, ItemDefinition item, ContentPack itemPack, Map<String, ContentPack> packs) {
        ResourceLocation model = ResourceLocation.parse(raw, item.id().namespace());
        validateModelReference(itemPack, packs, model, item.id());
        validateModelAsset(packs, model, item.id());
        return model;
    }

    private static int requireIndex(RenderAllocation allocation, CustomModelDataDefinition.ValueType type, String key, ContentID itemId) {
        StructuredModelDataAllocation structured = allocation.structuredModelData();
        Integer index = structured.index(type, key).orElse(null);
        if (index == null) {
            throw new JavaPackCompileException("Render rule references " + type.name().toLowerCase(java.util.Locale.ROOT)
                    + " custom_model_data key '" + key + "' without a stable allocation"
                    + (itemId == null ? "" : " for " + itemId));
        }
        return index.intValue();
    }

    private static Map<?, ?> requireMap(Object raw, String context, ContentID id) {
        if (!(raw instanceof Map)) throw new JavaPackCompileException("render.rule." + context + " must be a mapping for " + id);
        return (Map<?, ?>) raw;
    }

    private static String requireString(Object raw, String context, ContentID id) {
        if (!(raw instanceof String) || ((String) raw).trim().isEmpty()) throw new JavaPackCompileException(context + " must be a non-empty string for " + id);
        return ((String) raw).trim();
    }

    private static void requireOnly(Map<?, ?> map, ContentID id, String context, String... allowed) {
        for (Object key : map.keySet()) {
            boolean found = false;
            for (String candidate : allowed) if (candidate.equals(key)) { found = true; break; }
            if (!found) throw new JavaPackCompileException("Unsupported render.rule." + context + " key '" + key + "' for " + id);
        }
    }

    private static List<String> stringKeys(Map<?, ?> map, String context, ContentID id) {
        List<String> keys = new ArrayList<String>();
        for (Object key : map.keySet()) {
            if (!(key instanceof String)) throw new JavaPackCompileException(context + " keys must be strings for " + id);
            keys.add((String) key);
        }
        return keys;
    }

    private static void addOverride(List<ModelOverride> overrides, int value, ResourceLocation model, ContentID id) {
        for (ModelOverride existing : overrides) {
            if (existing.value != value) continue;
            if (existing.model.equals(model)) return;
            throw new JavaPackCompileException("Render predicate " + value + " for " + id + " conflicts between "
                    + existing.model + " and " + model);
        }
        overrides.add(new ModelOverride(value, model));
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
            if (!"minecraft".equals(material.namespace)) throw new JavaPackCompileException("Legacy damage rendering requires a minecraft base material: " + material);
            Material bukkit = Material.matchMaterial(material.path.toUpperCase(java.util.Locale.ROOT));
            if (bukkit == null || bukkit.getMaxDurability() <= 0) throw new JavaPackCompileException("Legacy damage rendering requires a damageable 1.12 material: " + material);
            int max = bukkit.getMaxDurability();
            Collections.sort(entry.getValue(), byValue());
            for (ModelOverride override : entry.getValue()) {
                if (override.value < 0 || override.value >= max) throw new JavaPackCompileException("durability " + override.value + " cannot be represented by " + material + " on 1.12; expected 0.." + (max - 1));
            }
            putEntry(entries, "assets/minecraft/models/item/" + material.path + ".json", utf8(legacyDamageModelJson(material, max, entry.getValue())));
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

    private static void writeCustomTextureAtlas(List<ContentPack> packs,
                                                JavaPackTarget target,
                                                Map<String, byte[]> entries) {
        String atlasPath = target.itemModelAtlasPath();
        if (atlasPath == null) return;

        java.util.SortedSet<String> directories = new java.util.TreeSet<String>();
        for (ContentPack pack : packs) {
            Path textures = pack.root().resolve("assets").resolve(pack.manifest().namespace()).resolve("textures");
            if (!Files.isDirectory(textures)) continue;
            try {
                java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(textures);
                try {
                    for (Path child : stream) {
                        if (!Files.isDirectory(child) || Files.isSymbolicLink(child)) continue;
                        String directory = child.getFileName().toString();
                        if ("block".equals(directory) || "item".equals(directory)) continue;
                        List<Path> files = new ArrayList<Path>();
                        collectFiles(child, files);
                        for (Path file : files) {
                            if (file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
                                directories.add(directory);
                                break;
                            }
                        }
                    }
                } finally { stream.close(); }
            } catch (IOException exception) {
                throw new JavaPackCompileException("Unable to discover custom texture directories for pack "
                        + pack.manifest().namespace(), exception);
            }
        }
        if (directories.isEmpty()) return;

        StringBuilder atlas = new StringBuilder("{\n  \"sources\": [\n");
        int index = 0;
        for (String directory : directories) {
            if (index++ > 0) atlas.append(",\n");
            atlas.append("    {\"type\": \"directory\", \"source\": \"")
                    .append(json(directory)).append("\", \"prefix\": \"")
                    .append(json(directory)).append("/\"}");
        }
        atlas.append("\n  ]\n}\n");
        putEntry(entries, atlasPath, utf8(atlas.toString()));
    }

    private static void writeUiFont(Map<String, byte[]> entries, UiGlyphRegistry glyphs) {
        StringBuilder out = new StringBuilder("{\n  \"providers\": [\n");
        out.append("    {\"type\":\"space\",\"advances\":{");
        boolean firstAdvance = true;
        for (Map.Entry<Integer, Integer> advance : UiSpacingGlyphs.advances().entrySet()) {
            if (!firstAdvance) out.append(',');
            firstAdvance = false;
            out.append('\"').append(new String(Character.toChars(advance.getKey().intValue())))
                    .append("\":").append(advance.getValue().intValue());
        }
        out.append("}}");

        List<UiGlyphDefinition> definitions = new ArrayList<UiGlyphDefinition>(glyphs.entries().values());
        Collections.sort(definitions, new Comparator<UiGlyphDefinition>() {
            @Override public int compare(UiGlyphDefinition left, UiGlyphDefinition right) {
                return left.id().toString().compareTo(right.id().toString());
            }
        });
        for (UiGlyphDefinition glyph : definitions) {
            out.append(",\n    {\"type\":\"bitmap\",\"file\":\"")
                    .append(json(glyph.texture())).append(".png\",\"ascent\":")
                    .append(glyph.ascent()).append(",\"height\":").append(glyph.height())
                    .append(",\"chars\":[\"").append(glyph.character()).append("\"]}");
        }
        String tooltipProviders = TooltipPackResources.defaultProviders();
        if (!tooltipProviders.isEmpty()) out.append(",\n    ").append(tooltipProviders);
        String containerProviders = ContainerGuiPackResources.defaultProviders();
        if (!containerProviders.isEmpty()) out.append(",\n    ").append(containerProviders);
        out.append("\n  ]\n}\n");
        String path = AuthoredFontSupport.DEFAULT_FONT_PATH;
        byte[] generated = utf8(out.toString());
        byte[] authored = entries.get(path);
        if (authored == null) putEntry(entries, path, generated);
        else entries.put(path, AuthoredFontSupport.mergeDefault(authored, generated));
    }


    private static void writeTooltipResources(Map<String, byte[]> entries) {
        for (Map.Entry<String, byte[]> resource : TooltipPackResources.entries().entrySet()) {
            putEntry(entries, resource.getKey(), resource.getValue());
        }
    }

    private static void writeContainerGuiResources(Map<String, byte[]> entries) {
        for (Map.Entry<String, byte[]> resource : ContainerGuiPackResources.entries().entrySet()) {
            putEntry(entries, resource.getKey(), resource.getValue());
        }
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
                            throw new JavaPackCompileException("Pack '" + pack.manifest().namespace() + "' may only author assets inside assets/"
                                    + pack.manifest().namespace() + "; found " + child.getFileName());
                        }
                    }
                } finally { stream.close(); }
            } catch (IOException exception) { throw new JavaPackCompileException("Unable to inspect assets for pack " + pack.manifest().namespace(), exception); }
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
        if (!packs.containsKey(model.namespace)) throw new JavaPackCompileException("Item " + itemId + " references model namespace '" + model.namespace + "' but no loaded pack owns that namespace");
        if (!model.namespace.equals(itemPack.manifest().namespace()) && !itemPack.manifest().dependsOn(model.namespace)) {
            throw new JavaPackCompileException("Item " + itemId + " references model " + model + " but pack '" + itemPack.manifest().namespace()
                    + "' does not declare dependency '" + model.namespace + "'");
        }
    }

    private static void validateModelAsset(Map<String, ContentPack> packs, ResourceLocation model, ContentID itemId) {
        ContentPack owner = packs.get(model.namespace);
        Path expected = owner.root().resolve("assets").resolve(model.namespace).resolve("models").resolve(model.path + ".json");
        if (!Files.isRegularFile(expected)) throw new JavaPackCompileException("Item " + itemId + " references missing model " + model + ". Expected " + expected);
    }

    private static boolean isGeneratedBlockModel(ResourceLocation model, ContentID itemId,
                                                   BlockDefinitionRegistry blocks) {
        return model.namespace.equals(itemId.namespace())
                && model.path.equals("block/" + itemId.value())
                && blocks.contains(itemId);
    }

    private static Integer legacyPredicateValue(ItemDefinition item, ItemRenderDefinition render,
                                                ResourceLocation model, BlockDefinitionRegistry blocks,
                                                RenderAllocation allocation) {
        if (render.durability() != null) return render.durability();
        return isGeneratedBlockModel(model, item.id(), blocks)
                ? Integer.valueOf(allocation.customModelData()) : null;
    }

    private static String packMeta(JavaPackTarget target) {
        if (target.usesRangeMetadata()) {
            return "{\n  \"pack\": {\n    \"min_format\": [" + target.packFormat() + ", " + target.packFormatMinor()
                    + "],\n    \"max_format\": [" + target.packFormat() + ", " + target.packFormatMinor()
                    + "],\n    \"description\": \"VoxelCore generated pack (" + json(target.id()) + ")\"\n  }\n}\n";
        }
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

    private static void writeDeterministicZip(Path output, TreeMap<String, byte[]> entries) {
        DeterministicZipWriter.write(output, entries);
    }

    private static void putEntry(Map<String, byte[]> entries, String path, byte[] content) {
        byte[] previous = entries.put(path, content);
        if (previous != null && !java.util.Arrays.equals(previous, content)) throw new JavaPackCompileException("Resource pack path collision: " + path);
    }

    private static byte[] utf8(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }

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
                    || path.startsWith("/") || path.endsWith("/") || path.contains("..")) throw new JavaPackCompileException("Invalid resource location: " + input);
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

    private static final class RangeCase {
        private final double threshold;
        private final Object node;
        private RangeCase(double threshold, Object node) { this.threshold = threshold; this.node = node; }
    }
}
