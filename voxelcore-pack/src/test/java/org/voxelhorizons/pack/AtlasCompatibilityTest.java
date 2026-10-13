package org.voxelhorizons.pack;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.Assert.*;

public class AtlasCompatibilityTest {
    private static byte[] text(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static String read(Map<String, byte[]> entries, String key) {
        return new String(entries.get(key), StandardCharsets.UTF_8);
    }

    @Test public void acceptsTabIndentedAuthoredModels() {
        Map<String, byte[]> entries = new TreeMap<String, byte[]>();
        String help = "assets/voxel/models/block/guide/help.json";
        String watering = "assets/voxel/models/farming/tools/watering_can.json";
        String helpJson = "{\n\t\"format_version\": \"1.21.11\",\n\t\"textures\": {\"particle\": \"minecraft:block/oak_planks\"}\n}";
        String wateringJson = "{\n\t\"__comment\": \"Updated by Flopsi\",\n\t\"textures\": {\"layer0\": \"minecraft:item/stick\"}\n}";
        entries.put(help, text(helpJson));
        entries.put(watering, text(wateringJson));
        AtlasCompatibility.repair(entries);
        assertEquals(helpJson, read(entries, help));
        assertEquals(wateringJson, read(entries, watering));
    }

    @Test public void acceptsTabIndentedAtlasSources() {
        byte[] authored = text("{\n\t\"sources\": [{\"type\": \"minecraft:directory\", \"source\": \"ui\", \"prefix\": \"ui/\"}]\n}");
        String merged = new String(AtlasCompatibility.merge(authored, authored,
                "assets/minecraft/atlases/items.json"), StandardCharsets.UTF_8);
        assertTrue(merged.contains("\"source\":\"ui\""));
    }

    @Test public void preservesVanillaSourcesAlongsideGeneratedDirectories() {
        Map<String, byte[]> entries = new TreeMap<String, byte[]>();
        entries.put("assets/minecraft/atlases/items.json",
                text("{\"sources\":[{\"type\":\"directory\",\"source\":\"ui\",\"prefix\":\"ui/\"}]}"));
        AtlasCompatibility.repair(entries);
        String output = read(entries, "assets/minecraft/atlases/items.json");
        assertTrue(output.contains("paletted_permutations"));
        assertTrue(output.contains("trim_palette"));
        assertTrue(output.contains("\"source\":\"item\""));
        assertTrue(output.contains("\"source\":\"ui\""));
    }

    @Test public void repairsMixedShelfParentAndLeavesTextureOnlyChildInherited() {
        Map<String, byte[]> entries = new TreeMap<String, byte[]>();
        String prefix = "assets/voxel/models/furniture/basic/shelf/wall/";
        entries.put(prefix + "oak_wall_shelf_left.json",
                text("{\"textures\":{\"0\":\"minecraft:block/oak_planks\","
                        + "\"1\":\"minecraft:item/stick\",\"particle\":\"#0\"},"
                        + "\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16],"
                        + "\"faces\":{\"north\":{\"texture\":\"#1\"}}}]}"));
        entries.put(prefix + "birch_wall_shelf_left.json",
                text("{\"parent\":\"voxel:furniture/basic/shelf/wall/oak_wall_shelf_left\","
                        + "\"textures\":{\"0\":\"minecraft:block/birch_planks\"}}"));
        AtlasCompatibility.repair(entries);
        String parent = read(entries, prefix + "oak_wall_shelf_left.json");
        assertTrue(parent.contains("voxelcore:atlas_compat/blocks/minecraft/item/stick"));
        String child = read(entries, prefix + "birch_wall_shelf_left.json");
        assertFalse(child.contains("atlas_compat"));
        String atlas = read(entries, "assets/minecraft/atlases/blocks.json");
        assertTrue(atlas.contains("\"resource\":\"minecraft:item/stick\""));
        assertTrue(atlas.contains("\"sprite\":\"voxelcore:atlas_compat/blocks/minecraft/item/stick\""));
    }

    @Test public void leavesSingleAtlasModelsUntouched() {
        Map<String, byte[]> entries = new TreeMap<String, byte[]>();
        String path = "assets/voxel/models/block/example.json";
        byte[] original = text("{\"textures\":{\"0\":\"minecraft:block/oak_planks\"}}");
        entries.put(path, original);
        AtlasCompatibility.repair(entries);
        assertArrayEquals(original, entries.get(path));
        assertFalse(entries.containsKey("assets/minecraft/atlases/blocks.json"));
    }

    @Test public void authoredAtlasSourcesMergeWithoutDuplicates() {
        byte[] source = text("{\"sources\":[{\"type\":\"minecraft:single\","
                + "\"resource\":\"minecraft:item/stick\",\"sprite\":\"voxel:test\"}]}");
        byte[] merged = AtlasCompatibility.merge(source, source, "assets/minecraft/atlases/blocks.json");
        String output = new String(merged, StandardCharsets.UTF_8);
        assertEquals(1, output.split("\"sprite\":\"voxel:test\"", -1).length - 1);
    }

    @Test public void fixesVehicleWithVanillaChestTextureWithoutChangingGeometryOrTint() {
        Map<String, byte[]> entries = new TreeMap<String, byte[]>();
        String vehicle = "assets/voxel/models/vehicles/farming/tractor_overhead_chest.json";
        String original = "{\"textures\":{\"1\":\"entity/chest/normal\","
                + "\"2\":\"voxel:vehicles/farming/tractor\","
                + "\"particle\":\"voxel:vehicles/farming/tractor\"},"
                + "\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16],"
                + "\"faces\":{\"north\":{\"texture\":\"#2\",\"tintindex\":0},"
                + "\"south\":{\"texture\":\"#1\"}}}]}";
        entries.put(vehicle, text(original));
        entries.put("assets/voxel/items/vehicles/farming/tractor_overhead_chest.json",
                text("{\"model\":{\"type\":\"minecraft:model\","
                        + "\"model\":\"voxel:vehicles/farming/tractor_overhead_chest\"}}"));
        AtlasCompatibility.repair(entries);

        String repaired = read(entries, vehicle);
        assertTrue(repaired.contains("\"1\":\"voxelcore:atlas_compat/items/minecraft/entity/chest/normal\""));
        assertTrue(repaired.contains("\"2\":\"voxel:vehicles/farming/tractor\""));
        assertTrue(repaired.contains("\"particle\":\"voxel:vehicles/farming/tractor\""));
        assertTrue(repaired.contains("\"tintindex\":0"));
        assertTrue(repaired.contains("\"texture\":\"#1\""));
        String items = read(entries, "assets/minecraft/atlases/items.json");
        assertTrue(items.contains("\"resource\":\"minecraft:entity/chest/normal\""));
        assertTrue(items.contains("\"sprite\":\"voxelcore:atlas_compat/items/minecraft/entity/chest/normal\""));
        assertFalse(entries.containsKey("assets/minecraft/atlases/blocks.json"));

        // Repairing after another authored-asset merge must not create duplicate aliases.
        AtlasCompatibility.repair(entries);
        assertEquals(1, read(entries, "assets/minecraft/atlases/items.json")
                .split("voxelcore:atlas_compat/items/minecraft/entity/chest/normal", -1).length - 1);
    }

    @Test public void resolvesModelReferencesNestedInCompositeItemProviders() {
        Map<String, byte[]> entries = new TreeMap<String, byte[]>();
        String vehicle = "assets/voxel/models/vehicles/farming/tractor_overhead_chest.json";
        entries.put(vehicle, text("{\"textures\":{\"1\":\"minecraft:entity/chest/normal\","
                + "\"2\":\"voxel:vehicles/farming/tractor\"}}"));
        entries.put("assets/voxel/items/vehicles/farming/tractor_overhead_chest.json",
                text("{\"model\":{\"type\":\"minecraft:composite\",\"models\":["
                        + "{\"type\":\"minecraft:model\","
                        + "\"model\":\"voxel:vehicles/farming/tractor_overhead_chest\"}]}}"));
        AtlasCompatibility.repair(entries);
        assertTrue(read(entries, vehicle).contains("voxelcore:atlas_compat/items/minecraft/entity/chest/normal"));
        assertTrue(read(entries, "assets/minecraft/atlases/items.json").contains("minecraft:entity/chest/normal"));
    }

    @Test public void keepsBlockAtlasPreferenceForModelsNotUsedByItemProviders() {
        Map<String, byte[]> entries = new TreeMap<String, byte[]>();
        String path = "assets/voxel/models/block/example.json";
        entries.put(path, text("{\"textures\":{\"chest\":\"entity/chest/normal\","
                + "\"item\":\"minecraft:item/stick\"}}"));
        AtlasCompatibility.repair(entries);
        String model = read(entries, path);
        assertTrue(model.contains("\"chest\":\"entity/chest/normal\""));
        assertTrue(model.contains("\"item\":\"voxelcore:atlas_compat/blocks/minecraft/item/stick\""));
        assertTrue(read(entries, "assets/minecraft/atlases/blocks.json")
                .contains("\"resource\":\"minecraft:item/stick\""));
    }
}
