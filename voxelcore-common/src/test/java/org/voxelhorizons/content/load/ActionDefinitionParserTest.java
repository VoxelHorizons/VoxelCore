package org.voxelhorizons.content.load;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.action.ActionType;
import org.voxelhorizons.content.item.ItemDefinition;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ActionDefinitionParserTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void parsesNestedItemInteractionActions() throws Exception {
        File root = temporaryFolder.newFolder("content");
        File pack = new File(root, "test");
        assertTrue(new File(pack, "content").mkdirs());
        write(new File(pack, "pack.yml"), "schema: 1\nnamespace: test\n");
        write(new File(pack, "content/actions.yml"),
                "items:\n  ore_icon:\n    material: minecraft:paper\n    events:\n      interact:\n        right:\n          actions:\n            - type: set_block\n              block: test:ruby_ore\n              target: relative\n              consume: 1\n" +
                "blocks:\n  ruby_ore:\n    texture: test:block/ruby_ore\n    drop_when_mined: false\n");
        ContentLoader loader = new ContentLoader();
        ItemDefinition item = loader.load(root.toPath()).get(ContentID.of("test", "ore_icon")).get();
        assertEquals(ActionType.SET_BLOCK, item.events().forEvent("interact.right").get(0).type());
        assertEquals("test:ruby_ore", item.events().forEvent("interact.right").get(0).string("block"));
        assertEquals(1, loader.loadBlocks(root.toPath()).size());
    }

    private static void write(File file, String value) throws Exception {
        Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
    }
}
