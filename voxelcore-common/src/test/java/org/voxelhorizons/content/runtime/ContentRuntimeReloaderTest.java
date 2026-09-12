package org.voxelhorizons.content.runtime;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.load.ContentLoader;
import org.voxelhorizons.item.ItemManager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ContentRuntimeReloaderTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void publishesSuccessfulReloadAndPreservesPreviousSnapshotOnFailure() throws Exception {
        File root = temporaryFolder.newFolder("content");
        File pack = new File(root, "pack");
        File definitions = new File(pack, "definitions");
        assertTrue(definitions.mkdirs());

        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: test\n");
        File items = new File(definitions, "items.yml");
        write(items,
                "items:\n" +
                "  first:\n" +
                "    material: minecraft:paper\n");

        ContentLoader loader = new ContentLoader();
        ItemDefinitionRegistry initial = loader.load(root.toPath());
        ContentRuntime runtime = new ContentRuntime(new ContentSnapshot(1L, initial));
        ContentRuntimeReloader reloader = new ContentRuntimeReloader(loader, root.toPath(), runtime);
        ItemManager manager = new ItemManager(runtime, null);

        assertTrue(manager.hasItem(ContentID.of("test", "first")));
        assertEquals(1L, runtime.current().revision());

        write(items,
                "items:\n" +
                "  second:\n" +
                "    material: minecraft:stone\n");

        ContentReloadResult success = reloader.reload();
        assertTrue(success.success());
        assertEquals(2L, success.activeRevision());
        assertEquals(1, success.itemCount());
        assertFalse(manager.hasItem(ContentID.of("test", "first")));
        assertTrue(manager.hasItem(ContentID.of("test", "second")));

        write(items,
                "items:\n" +
                "  broken:\n" +
                "    extends: missing_parent\n");

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
