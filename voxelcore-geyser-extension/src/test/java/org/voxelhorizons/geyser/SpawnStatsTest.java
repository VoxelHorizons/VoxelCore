package org.voxelhorizons.geyser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SpawnStatsTest {
    @Test public void describesEachOutcome() {
        SpawnStats stats = new SpawnStats();
        stats.substituted.incrementAndGet();
        stats.notInFeed.addAndGet(3);
        stats.unregistered("voxelcore:entity_i_a");
        stats.unregistered("voxelcore:entity_i_a");
        stats.otherType("minecraft:armor_stand");
        assertEquals("1 substituted, 3 item_display not in a projection feed, 0 rejected by Geyser; "
                + "fed models whose Bedrock entity is not registered (restart Geyser after a pack build): 1 "
                + "[voxelcore:entity_i_a x2]; fed UUIDs spawned as other entity types (not substituted): "
                + "[minecraft:armor_stand x1]; spawn events from Geyser: 0", stats.describe());
        stats.seen("minecraft:pig");
        assertEquals(true, stats.describe().endsWith("spawn events from Geyser: 1 [minecraft:pig x1]"));
    }
}
