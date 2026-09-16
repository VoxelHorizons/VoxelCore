package org.voxelhorizons.pack;

import org.voxelhorizons.content.block.BlockAllocation;
import org.voxelhorizons.content.block.BlockAllocationRegistry;
import org.voxelhorizons.content.block.BlockCarrierStates;
import org.voxelhorizons.content.block.BlockDefinition;
import org.voxelhorizons.content.block.BlockDefinitionRegistry;
import org.voxelhorizons.content.block.BlockModelPreset;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Generates block models and complete carrier blockstate tables. */
final class BlockPackCompiler {
    private static final String[] LEGACY_VARIANTS = {
            "all_inside", "north_west", "north", "north_east", "west", "center", "east",
            "south_west", "south", "south_east", "stem", "all_outside", "all_stem"
    };
    private static final String[] NOTE_INSTRUMENTS = {
            "harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar", "chime", "xylophone",
            "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo", "pling", "zombie", "skeleton",
            "creeper", "dragon", "wither_skeleton", "piglin", "custom_head"
    };

    private BlockPackCompiler() {}

    static int write(TreeMap<String, byte[]> entries, BlockDefinitionRegistry blocks,
                     BlockAllocationRegistry allocations, boolean modern) {
        Map<String, Map<String, String>> customStates = new LinkedHashMap<String, Map<String, String>>();
        List<BlockDefinition> sorted = new ArrayList<BlockDefinition>(blocks.entries().values());
        Collections.sort(sorted, new Comparator<BlockDefinition>() {
            @Override public int compare(BlockDefinition left, BlockDefinition right) {
                return left.id().toString().compareTo(right.id().toString());
            }
        });
        int rendered = 0;
        for (BlockDefinition definition : sorted) {
            if (definition.abstractDefinition()) continue;
            BlockAllocation allocation = allocations.get(definition.id()).orElseThrow(
                    () -> new JavaPackCompileException("Missing block allocation for " + definition.id()));
            String model = definition.id().namespace() + ":block/" + definition.id().path();
            put(entries, "assets/" + definition.id().namespace() + "/models/block/" + definition.id().path() + ".json",
                    modelJson(definition));
            String state = BlockCarrierStates.state(allocation, modern);
            String carrier = modern ? BlockCarrierStates.blockName(state) : legacyCarrier(state);
            String properties = modern ? BlockCarrierStates.properties(state) : legacyVariant(state);
            Map<String, String> variants = customStates.get(carrier);
            if (variants == null) {
                variants = new LinkedHashMap<String, String>();
                customStates.put(carrier, variants);
            }
            variants.put(properties, model);
            rendered++;
        }
        if (modern) writeModernCarrierTables(entries, customStates);
        else writeLegacyCarrierTables(entries, customStates);
        return rendered;
    }

    private static void writeModernCarrierTables(TreeMap<String, byte[]> entries,
                                                 Map<String, Map<String, String>> custom) {
        Map<String, String> notes = custom.get("minecraft:note_block");
        if (notes != null) {
            Map<String, String> complete = new LinkedHashMap<String, String>();
            for (String instrument : NOTE_INSTRUMENTS) {
                for (int note = 0; note < 25; note++) {
                    for (boolean powered : new boolean[]{false, true}) {
                        String key = "instrument=" + instrument + ",note=" + note + ",powered=" + powered;
                        complete.put(key, notes.containsKey(key) ? notes.get(key) : "minecraft:block/note_block");
                    }
                }
            }
            put(entries, "assets/minecraft/blockstates/note_block.json", variantsJson(complete));
        }
        for (String carrier : new String[]{"minecraft:brown_mushroom_block", "minecraft:red_mushroom_block", "minecraft:mushroom_stem"}) {
            Map<String, String> allocated = custom.get(carrier);
            if (allocated == null) continue;
            Map<String, String> complete = new LinkedHashMap<String, String>();
            for (int bits = 0; bits < 64; bits++) {
                String key = "down=" + bit(bits, 0) + ",east=" + bit(bits, 1) + ",north=" + bit(bits, 2)
                        + ",south=" + bit(bits, 3) + ",up=" + bit(bits, 4) + ",west=" + bit(bits, 5);
                String fallback = carrier.substring("minecraft:".length());
                complete.put(key, allocated.containsKey(key) ? allocated.get(key) : "minecraft:block/" + fallback);
            }
            put(entries, "assets/minecraft/blockstates/" + carrier.substring("minecraft:".length()) + ".json",
                    variantsJson(complete));
        }
    }

    private static void writeLegacyCarrierTables(TreeMap<String, byte[]> entries,
                                                 Map<String, Map<String, String>> custom) {
        for (String carrier : new String[]{"brown_mushroom_block", "red_mushroom_block"}) {
            Map<String, String> allocated = custom.get(carrier);
            if (allocated == null) continue;
            Map<String, String> complete = new LinkedHashMap<String, String>();
            for (String variant : LEGACY_VARIANTS) {
                String key = "variant=" + variant;
                complete.put(key, allocated.containsKey(key) ? allocated.get(key) : "minecraft:block/" + carrier);
            }
            put(entries, "assets/minecraft/blockstates/" + carrier + ".json", variantsJson(complete));
        }
    }

    private static String modelJson(BlockDefinition definition) {
        Map<String, String> textures = definition.textures();
        StringBuilder out = new StringBuilder("{\n  \"parent\": \"");
        if (definition.model() == BlockModelPreset.CUBE_ALL) {
            out.append("minecraft:block/cube_all\",\n  \"textures\": {\"all\": \"").append(textures.get("all")).append("\"}");
        } else if (definition.model() == BlockModelPreset.CUBE_COLUMN) {
            if (textures.get("top").equals(textures.get("bottom"))) {
                out.append("minecraft:block/cube_column\",\n  \"textures\": {\"side\": \"").append(textures.get("side"))
                        .append("\", \"end\": \"").append(textures.get("top")).append("\"}");
            } else {
                out.append("minecraft:block/cube\",\n  \"textures\": {")
                        .append("\"north\": \"").append(textures.get("side")).append("\", ")
                        .append("\"south\": \"").append(textures.get("side")).append("\", ")
                        .append("\"east\": \"").append(textures.get("side")).append("\", ")
                        .append("\"west\": \"").append(textures.get("side")).append("\", ")
                        .append("\"up\": \"").append(textures.get("top")).append("\", ")
                        .append("\"down\": \"").append(textures.get("bottom")).append("\"}");
            }
        } else {
            out.append("minecraft:block/cube\",\n  \"textures\": {");
            boolean first = true;
            for (String face : new String[]{"north", "south", "east", "west", "up", "down"}) {
                if (!first) out.append(", ");
                out.append("\"").append(face).append("\": \"").append(textures.get(face)).append("\"");
                first = false;
            }
            out.append('}');
        }
        return out.append("\n}\n").toString();
    }

    private static String variantsJson(Map<String, String> variants) {
        StringBuilder out = new StringBuilder("{\n  \"variants\": {\n");
        boolean first = true;
        for (Map.Entry<String, String> entry : variants.entrySet()) {
            if (!first) out.append(",\n");
            out.append("    \"").append(entry.getKey()).append("\": {\"model\": \"")
                    .append(entry.getValue()).append("\"}");
            first = false;
        }
        return out.append("\n  }\n}\n").toString();
    }

    private static String legacyCarrier(String state) {
        return state.contains("HUGE_MUSHROOM_1") ? "brown_mushroom_block" : "red_mushroom_block";
    }

    private static String legacyVariant(String state) {
        int data = Integer.parseInt(state.substring(state.lastIndexOf(':') + 1));
        int index = data <= 10 ? data : data == 14 ? 11 : 12;
        return "variant=" + LEGACY_VARIANTS[index];
    }

    private static boolean bit(int value, int bit) { return (value & (1 << bit)) != 0; }
    private static void put(TreeMap<String, byte[]> entries, String path, String value) {
        if (entries.put(path, value.getBytes(StandardCharsets.UTF_8)) != null) {
            throw new JavaPackCompileException("Generated resource collision at " + path);
        }
    }
}
