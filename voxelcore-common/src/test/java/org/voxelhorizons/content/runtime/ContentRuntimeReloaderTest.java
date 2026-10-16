package org.voxelhorizons.content.runtime;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.load.ContentLoader;
import org.voxelhorizons.content.load.ContentDefinitions;
import org.voxelhorizons.content.block.BlockDefinitionRegistry;
import org.voxelhorizons.content.block.BlockAllocationRegistry;
import org.voxelhorizons.content.render.RenderAllocationRegistry;
import org.voxelhorizons.item.ItemManager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ContentRuntimeReloaderTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void publishesSuccessfulReloadAndPreservesPreviousSnapshotOnFailure() throws Exception {
        File root = temporaryFolder.newFolder("content");
        File pack = new File(root, "pack");
        File content = new File(pack, "content");
        assertTrue(content.mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: test\n");
        File items = new File(content, "items.yml");
        write(items, "items:\n  first:\n    material: minecraft:paper\n");

        ContentLoader loader = new ContentLoader();
        ItemDefinitionRegistry initial = loader.load(root.toPath());
        ContentRuntime runtime = new ContentRuntime(new ContentSnapshot(1L, initial));
        ContentRuntimeReloader reloader = new ContentRuntimeReloader(loader, root.toPath(), runtime);
        ItemManager manager = new ItemManager(runtime, null);

        assertTrue(manager.hasItem(ContentID.of("test", "first")));
        assertEquals(1L, runtime.current().revision());

        write(items, "items:\n  second:\n    material: minecraft:stone\n");
        ContentReloadResult success = reloader.reload();
        assertTrue(success.success());
        assertEquals(2L, success.activeRevision());
        assertEquals(1, success.itemCount());
        assertFalse(manager.hasItem(ContentID.of("test", "first")));
        assertTrue(manager.hasItem(ContentID.of("test", "second")));

        write(items, "items:\n  broken:\n    extends: missing_parent\n");
        ContentReloadResult failure = reloader.reload();
        assertFalse(failure.success());
        assertEquals(2L, failure.activeRevision());
        assertEquals(1, failure.itemCount());
        assertTrue(failure.message().contains("missing_parent"));
        assertTrue(manager.hasItem(ContentID.of("test", "second")));
        assertFalse(manager.hasItem(ContentID.of("test", "broken")));
        assertEquals(2L, runtime.current().revision());
    }

    @Test
    public void validatorFailurePreservesPreviousSnapshot() throws Exception {
        File root = temporaryFolder.newFolder("validated-content");
        File pack = new File(root, "pack");
        File content = new File(pack, "content");
        assertTrue(content.mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: test\n");
        File items = new File(content, "items.yml");
        write(items, "items:\n  first:\n    material: minecraft:paper\n");

        ContentLoader loader = new ContentLoader();
        ItemDefinitionRegistry initial = loader.load(root.toPath());
        ContentRuntime runtime = new ContentRuntime(new ContentSnapshot(7L, initial));
        write(items, "items:\n  second:\n    material: minecraft:stone\n");

        ContentRuntimeReloader reloader = new ContentRuntimeReloader(loader, root.toPath(), runtime, null,
                new ContentSnapshotValidator() {
                    @Override public void validate(ItemDefinitionRegistry candidate, RenderAllocationRegistry allocations) {
                        throw new IllegalArgumentException("platform validation failed");
                    }
                });
        ContentReloadResult failure = reloader.reload();
        assertFalse(failure.success());
        assertTrue(failure.message().contains("platform validation failed"));
        assertEquals(7L, runtime.current().revision());
        assertTrue(runtime.current().items().contains(ContentID.of("test", "first")));
        assertFalse(runtime.current().items().contains(ContentID.of("test", "second")));
    }

    @Test
    public void preparedReloadPublishesTheValidatedCandidateWithoutReparsingDisk() throws Exception {
        File root = temporaryFolder.newFolder("prepared-content");
        File pack = new File(root, "pack");
        File content = new File(pack, "content");
        assertTrue(content.mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: test\n");
        File items = new File(content, "items.yml");
        write(items, "items:\n  first:\n    material: minecraft:paper\n");

        ContentLoader loader = new ContentLoader();
        ContentDefinitions initial = loader.loadDefinitions(root.toPath());
        ContentRuntime runtime = new ContentRuntime(new ContentSnapshot(1L, initial.items()));
        final boolean[] validated = {false};
        ContentRuntimeReloader reloader = new ContentRuntimeReloader(loader, root.toPath(), runtime, null,
                new ContentSnapshotValidator() {
                    @Override public void validate(ItemDefinitionRegistry items, RenderAllocationRegistry allocations) {
                        assertTrue(items.contains(ContentID.of("test", "second")));
                    }
                    @Override public void validateBlocks(BlockDefinitionRegistry blocks, BlockAllocationRegistry allocations,
                                                         ItemDefinitionRegistry items) {
                        validated[0] = true;
                        assertTrue(items.contains(ContentID.of("test", "second")));
                        assertFalse(items.contains(ContentID.of("test", "first")));
                    }
                });
        write(items, "items:\n  second:\n    material: minecraft:stone\n");
        ContentDefinitions prepared = reloader.prepare();

        // Publishing a prepared snapshot must not parse the files again. Reloading
        // would now fail because the file on disk is deliberately invalid.
        write(items, "items:\n  broken:\n    extends: missing_parent\n");
        ContentReloadResult result = reloader.reloadPrepared(prepared);
        assertTrue(result.success());
        assertTrue(validated[0]);
        assertEquals(2L, result.activeRevision());
        assertTrue(runtime.current().items().contains(ContentID.of("test", "second")));
        assertFalse(runtime.current().items().contains(ContentID.of("test", "first")));

        // The existing synchronous command still validates disk content and refuses it.
        assertFalse(reloader.reload().success());
        assertEquals(2L, runtime.current().revision());
    }

    @Test
    public void publishOnlyIncrementsRevisionAfterPublication() {
        ItemDefinitionRegistry empty = new ItemDefinitionRegistry(
                java.util.Collections.<ContentID, org.voxelhorizons.content.item.ItemDefinition>emptyMap());
        ContentRuntime runtime = new ContentRuntime(new ContentSnapshot(0L, empty));
        assertEquals(0L, runtime.current().revision());
        ContentSnapshot published = runtime.publish(empty);
        assertEquals(1L, published.revision());
        assertEquals(1L, runtime.current().revision());
    }

    private static void write(File file, String content) throws Exception {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
}
