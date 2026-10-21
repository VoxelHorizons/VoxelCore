package org.voxelhorizons.pack;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class BlockStateCompatibilityTest {
    @Test
    public void expandsAuthoredNoteSelectorsWithoutHidingVoxelCoreAllocations() {
        String generated = "{\"variants\":{" +
                "\"instrument=harp,note=0,powered=false\":{\"model\":\"minecraft:block/note_block\"}," +
                "\"instrument=harp,note=0,powered=true\":{\"model\":\"test:block/custom_note\"}," +
                "\"instrument=bell,note=0,powered=false\":{\"model\":\"minecraft:block/note_block\"}," +
                "\"instrument=bell,note=1,powered=false\":{\"model\":\"minecraft:block/note_block\"}}}";
        String authored = "{\"variants\":{" +
                "\"note=0\":{\"model\":\"block/note_block_0\"}," +
                "\"note=1\":{\"model\":\"block/note_block_1\"}}}";

        String merged = new String(BlockStateCompatibility.merge(bytes(generated), bytes(authored),
                "assets/minecraft/blockstates/note_block.json"), StandardCharsets.UTF_8);

        assertTrue(merged.contains("instrument=harp,note=0,powered=false\":{\"model\":\"block/note_block_0"));
        assertTrue(merged.contains("instrument=bell,note=0,powered=false\":{\"model\":\"block/note_block_0"));
        assertTrue(merged.contains("instrument=bell,note=1,powered=false\":{\"model\":\"block/note_block_1"));
        assertTrue(merged.contains("instrument=harp,note=0,powered=true\":{\"model\":\"test:block/custom_note"));
        assertFalse(merged.contains("\"note=0\""));
    }

    @Test
    public void addsAuthoredMultipartRulesToGeneratedBlockstate() {
        String generated = "{\"variants\":{\"north=false\":{\"model\":\"minecraft:block/fence\"}},\"multipart\":[{\"apply\":{\"model\":\"test:block/generated\"}}]}";
        String authored = "{\"multipart\":[{\"apply\":{\"model\":\"test:block/authored\"}}]}";
        String merged = new String(BlockStateCompatibility.merge(bytes(generated), bytes(authored),
                "assets/minecraft/blockstates/oak_fence.json"), StandardCharsets.UTF_8);
        assertTrue(merged.contains("test:block/generated"));
        assertTrue(merged.contains("test:block/authored"));
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
