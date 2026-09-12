package org.voxelhorizons.content.render;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.load.ContentLoader;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RenderAllocationRegistryTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void allocatesPersistsAndTombstonesStableNumericValues() throws Exception {
        File root = temporaryFolder.newFolder("content");
        File pack = new File(root, "pack");
        File content = new File(pack, "content");
        assertTrue(content.mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: test\n");
        File items = new File(content, "items.yml");
        write(items,
                "items:\n" +
                "  ruby:\n" +
                "    material: minecraft:paper\n" +
                "    render:\n" +
                "      model: test:item/ruby\n" +
                "  sapphire:\n" +
                "    material: minecraft:paper\n" +
                "    render:\n" +
                "      model: test:item/sapphire\n" +
                "      custom_model_data: 1200\n");

        ItemDefinitionRegistry firstItems = new ContentLoader().load(root.toPath());
        RenderAllocationRegistry first = RenderAllocationRegistry.reconcile(firstItems, RenderAllocationRegistry.empty());
        assertEquals(1000, first.get(ContentID.of("test", "ruby")).get().customModelData());
        assertEquals(1200, first.get(ContentID.of("test", "sapphire")).get().customModelData());

        File manifest = temporaryFolder.newFile("render-allocations.yml");
        RenderAllocationStore store = new RenderAllocationStore(manifest.toPath());
        store.save(first);
        RenderAllocationRegistry loaded = store.load();
        assertEquals(1000, loaded.get(ContentID.of("test", "ruby")).get().customModelData());
        assertEquals(1200, loaded.get(ContentID.of("test", "sapphire")).get().customModelData());

        write(items,
                "items:\n" +
                "  emerald:\n" +
                "    material: minecraft:paper\n" +
                "    render:\n" +
                "      model: test:item/emerald\n");
        ItemDefinitionRegistry secondItems = new ContentLoader().load(root.toPath());
        RenderAllocationRegistry second = RenderAllocationRegistry.reconcile(secondItems, loaded);

        assertFalse(second.get(ContentID.of("test", "ruby")).get().active());
        assertFalse(second.get(ContentID.of("test", "sapphire")).get().active());
        assertEquals(1201, second.get(ContentID.of("test", "emerald")).get().customModelData());
        assertTrue(second.get(ContentID.of("test", "emerald")).get().active());
    }

    @Test
    public void inheritedItemsMayShareExplicitAllocationForSameModel() throws Exception {
        File root = temporaryFolder.newFolder("inherited-content");
        File pack = new File(root, "pack");
        File content = new File(pack, "content");
        assertTrue(content.mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: test\n");
        write(new File(content, "items.yml"),
                "items:\n" +
                "  red_item:\n" +
                "    material: minecraft:diamond_hoe\n" +
                "    render:\n" +
                "      model: test:item/red_item\n" +
                "      custom_model_data: 5\n" +
                "  child_item:\n" +
                "    extends: red_item\n" +
                "    display_name: Child\n");

        RenderAllocationRegistry registry = RenderAllocationRegistry.reconcile(
                new ContentLoader().load(root.toPath()), RenderAllocationRegistry.empty());
        assertEquals(5, registry.get(ContentID.of("test", "red_item")).get().customModelData());
        assertEquals(5, registry.get(ContentID.of("test", "child_item")).get().customModelData());
        assertEquals("test:item/red_item", registry.get(ContentID.of("test", "child_item")).get().model());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSameExplicitAllocationForDifferentModels() throws Exception {
        File root = temporaryFolder.newFolder("collision-content");
        File pack = new File(root, "pack");
        File content = new File(pack, "content");
        assertTrue(content.mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: test\n");
        write(new File(content, "items.yml"),
                "items:\n" +
                "  ruby:\n" +
                "    material: minecraft:paper\n" +
                "    render:\n" +
                "      model: test:item/ruby\n" +
                "      custom_model_data: 5\n" +
                "  sapphire:\n" +
                "    material: minecraft:paper\n" +
                "    render:\n" +
                "      model: test:item/sapphire\n" +
                "      custom_model_data: 5\n");
        RenderAllocationRegistry.reconcile(new ContentLoader().load(root.toPath()), RenderAllocationRegistry.empty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsChangingAnExistingExplicitAllocation() throws Exception {
        File root = temporaryFolder.newFolder("explicit-content");
        File pack = new File(root, "pack");
        File content = new File(pack, "content");
        assertTrue(content.mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: test\n");
        File items = new File(content, "items.yml");
        write(items, "items:\n  ruby:\n    material: minecraft:paper\n    render:\n      model: test:item/ruby\n      custom_model_data: 1500\n");
        ContentLoader loader = new ContentLoader();
        RenderAllocationRegistry first = RenderAllocationRegistry.reconcile(loader.load(root.toPath()), RenderAllocationRegistry.empty());
        write(items, "items:\n  ruby:\n    material: minecraft:paper\n    render:\n      model: test:item/ruby\n      custom_model_data: 1501\n");
        RenderAllocationRegistry.reconcile(loader.load(root.toPath()), first);
    }

    private static void write(File file, String content) throws Exception {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
}
