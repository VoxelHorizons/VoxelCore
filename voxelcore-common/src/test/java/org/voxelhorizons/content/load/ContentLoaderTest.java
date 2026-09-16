package org.voxelhorizons.content.load;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.content.item.ItemDefinitionRegistry;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ContentLoaderTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void createsPlaceableInventoryItemForBlockOnlyDefinition() throws Exception {
        File root = temporaryFolder.newFolder("implicit-block-item");
        File pack = new File(root, "voxelpack");
        File content = new File(pack, "content/blocks");
        assertTrue(content.mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: voxel\n");
        write(new File(content, "ores.yml"),
                "blocks:\n" +
                "  ruby_ore:\n" +
                "    display_name: '&cRuby Ore'\n" +
                "    texture: voxel:block/ore/ruby_ore\n" +
                "    hardness: 3.0\n" +
                "    blast_resistance: 3.0\n" +
                "    break_tools:\n" +
                "      - PICKAXE\n" +
                "    minimum_tool_tier: IRON\n" +
                "    drop: voxel:ruby_ore\n" +
                "    silk_touch: voxel:ruby_ore\n");

        ContentDefinitions definitions = new ContentLoader().loadDefinitions(root.toPath());
        ItemDefinition item = definitions.items().get(ContentID.of("voxel", "ruby_ore")).get();
        assertEquals("&cRuby Ore", item.displayName());
        assertEquals("voxel:block/ruby_ore", item.render().model());
        assertEquals("voxel:ruby_ore",
                item.events().forEvent("interact.right").get(0).string("block"));
        assertEquals(ContentID.of("voxel", "ruby_ore"),
                definitions.blocks().get(ContentID.of("voxel", "ruby_ore")).get().dropItem());
        assertEquals(Collections.singletonList("PICKAXE"),
                definitions.blocks().get(ContentID.of("voxel", "ruby_ore")).get().breakTools());
        assertEquals("IRON",
                definitions.blocks().get(ContentID.of("voxel", "ruby_ore")).get().minimumToolTier());
    }

    @Test
    public void loadsYamlAndCompilesInheritance() throws Exception {
        File root = temporaryFolder.newFolder("content");
        File pack = new File(root, "voxel_horizons");
        File content = new File(pack, "content");
        assertTrue(content.mkdirs());

        write(new File(pack, "pack.yml"),
                "schema: 1\n" +
                "namespace: voxelhorizons\n");

        write(new File(content, "items.yml"),
                "items:\n" +
                "  chair_base:\n" +
                "    material: minecraft:paper\n" +
                "    bound: true\n" +
                "    lore:\n" +
                "      - Base lore\n" +
                "    properties:\n" +
                "      furniture:\n" +
                "        seats: 1\n" +
                "        storage: false\n" +
                "  oak_chair:\n" +
                "    extends: chair_base\n" +
                "    display_name: Oak Chair\n" +
                "    bound: false\n" +
                "    render:\n" +
                "      model: voxelhorizons:furniture/oak_chair\n" +
                "    properties:\n" +
                "      furniture:\n" +
                "        storage: true\n");

        ItemDefinitionRegistry registry = new ContentLoader().load(root.toPath());
        assertEquals(2, registry.size());

        ItemDefinition chair = registry.get(ContentID.of("voxelhorizons", "oak_chair")).get();
        assertEquals("minecraft:paper", chair.material());
        assertEquals("Oak Chair", chair.displayName());
        assertFalse(chair.bound());
        assertEquals("Base lore", chair.lore().get(0));
        assertEquals("voxelhorizons:furniture/oak_chair", chair.render().model());

        java.util.Map<?, ?> furniture = (java.util.Map<?, ?>) chair.properties().get("furniture");
        assertEquals(1, ((Number) furniture.get("seats")).intValue());
        assertEquals(Boolean.TRUE, furniture.get("storage"));
    }

    @Test
    public void normalizesScalarYamlKeysInsideRenderRules() throws Exception {
        File root = temporaryFolder.newFolder("render-rule-keys");
        File pack = new File(root, "pack");
        File content = new File(pack, "content");
        assertTrue(content.mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: test\n");
        write(new File(content, "items.yml"),
                "items:\n" +
                "  thing:\n" +
                "    material: minecraft:paper\n" +
                "    render:\n" +
                "      model: test:item/thing\n" +
                "      custom_model_data:\n" +
                "        powered: true\n" +
                "        intensity: 0.75\n" +
                "      rule:\n" +
                "        condition:\n" +
                "          key: powered\n" +
                "          true:\n" +
                "            range:\n" +
                "              key: intensity\n" +
                "              entries:\n" +
                "                0.75: test:item/high\n" +
                "              fallback: test:item/thing\n" +
                "          false: test:item/thing\n");

        ItemDefinition item = new ContentLoader().load(root.toPath())
                .get(ContentID.of("test", "thing")).get();
        java.util.Map<?, ?> condition = (java.util.Map<?, ?>) item.render().rule().get("condition");
        assertTrue(condition.containsKey("true"));
        assertTrue(condition.containsKey("false"));
        java.util.Map<?, ?> trueBranch = (java.util.Map<?, ?>) condition.get("true");
        java.util.Map<?, ?> range = (java.util.Map<?, ?>) trueBranch.get("range");
        java.util.Map<?, ?> entries = (java.util.Map<?, ?>) range.get("entries");
        assertTrue(entries.containsKey("0.75"));
    }

    @Test
    public void loadsYamlRecursivelyFromNestedContentDirectories() throws Exception {
        File root = temporaryFolder.newFolder("recursive-content");
        File pack = new File(root, "my_pack");
        File vehicles = new File(pack, "content/vehicles");
        assertTrue(vehicles.mkdirs());

        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: test\n");
        write(new File(vehicles, "cars.yml"),
                "items:\n  car:\n    material: minecraft:paper\n");
        write(new File(vehicles, "trucks.yml"),
                "items:\n  truck:\n    material: minecraft:paper\n");

        ItemDefinitionRegistry registry = new ContentLoader().load(root.toPath());
        assertEquals(2, registry.size());
        assertTrue(registry.contains(ContentID.of("test", "car")));
        assertTrue(registry.contains(ContentID.of("test", "truck")));
    }

    @Test
    public void rejectsDuplicateIdsAcrossFiles() throws Exception {
        File root = temporaryFolder.newFolder("duplicates");
        File pack = new File(root, "pack");
        File content = new File(pack, "content");
        assertTrue(content.mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: test\n");
        write(new File(content, "a.yml"), "items:\n  thing:\n    material: minecraft:paper\n");
        write(new File(content, "b.yml"), "items:\n  thing:\n    material: minecraft:stone\n");

        try {
            new ContentLoader().load(root.toPath());
            fail("Expected duplicate id failure");
        } catch (ContentLoadException exception) {
            assertTrue(exception.getMessage().contains("Duplicate item id test:thing"));
            assertTrue(exception.getMessage().contains("a.yml"));
            assertTrue(exception.getMessage().contains("b.yml"));
        }
    }

    @Test
    public void rejectsUnsupportedItemKeys() throws Exception {
        File root = temporaryFolder.newFolder("unknown-key");
        File pack = new File(root, "pack");
        File content = new File(pack, "content");
        assertTrue(content.mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: test\n");
        write(new File(content, "items.yml"),
                "items:\n  thing:\n    material: minecraft:paper\n    typo_field: nope\n");

        try {
            new ContentLoader().load(root.toPath());
            fail("Expected unsupported-key failure");
        } catch (ContentLoadException exception) {
            assertTrue(exception.getMessage().contains("Unsupported key 'typo_field'"));
        }
    }

    private static void write(File file, String content) throws Exception {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
}
