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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ContentLoaderTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void loadsYamlAndCompilesInheritance() throws Exception {
        File root = temporaryFolder.newFolder("content");
        File pack = new File(root, "voxel_horizons");
        File definitions = new File(pack, "definitions");
        assertTrue(definitions.mkdirs());

        write(new File(pack, "pack.yml"),
                "schema: 1\n" +
                "namespace: voxelhorizons\n");

        write(new File(definitions, "items.yml"),
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
    public void rejectsDuplicateIdsAcrossFiles() throws Exception {
        File root = temporaryFolder.newFolder("duplicates");
        File pack = new File(root, "pack");
        File definitions = new File(pack, "definitions");
        assertTrue(definitions.mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: test\n");
        write(new File(definitions, "a.yml"), "items:\n  thing:\n    material: minecraft:paper\n");
        write(new File(definitions, "b.yml"), "items:\n  thing:\n    material: minecraft:stone\n");

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
        File definitions = new File(pack, "definitions");
        assertTrue(definitions.mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: test\n");
        write(new File(definitions, "items.yml"),
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
