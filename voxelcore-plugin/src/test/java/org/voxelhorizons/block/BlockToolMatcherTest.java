package org.voxelhorizons.block;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlockToolMatcherTest {
    @Test
    public void categoryAllowsEveryPickaxe() {
        assertTrue(BlockToolMatcher.canHarvest(Collections.singletonList("PICKAXE"), null,
                "WOODEN_PICKAXE", null));
        assertTrue(BlockToolMatcher.canHarvest(Collections.singletonList("PICKAXE"), null,
                "NETHERITE_PICKAXE", null));
        assertFalse(BlockToolMatcher.canHarvest(Collections.singletonList("PICKAXE"), null,
                "DIAMOND_AXE", null));
    }

    @Test
    public void exactMaterialAllowsOnlyThatTool() {
        assertTrue(BlockToolMatcher.canHarvest(Collections.singletonList("DIAMOND_PICKAXE"), null,
                "DIAMOND_PICKAXE", null));
        assertFalse(BlockToolMatcher.canHarvest(Collections.singletonList("DIAMOND_PICKAXE"), null,
                "NETHERITE_PICKAXE", null));
    }

    @Test
    public void minimumTierSupportsOreStyleRulesAndLegacyNames() {
        assertFalse(BlockToolMatcher.canHarvest(Collections.singletonList("PICKAXE"), "IRON",
                "STONE_PICKAXE", null));
        assertTrue(BlockToolMatcher.canHarvest(Collections.singletonList("PICKAXE"), "IRON",
                "IRON_PICKAXE", null));
        assertTrue(BlockToolMatcher.canHarvest(Collections.singletonList("PICKAXE"), "IRON",
                "NETHERITE_PICKAXE", null));
        assertTrue(BlockToolMatcher.canHarvest(Collections.singletonList("PICKAXE"), "WOOD",
                "GOLD_PICKAXE", null));
    }

    @Test
    public void customItemIdsCanBeWhitelisted() {
        assertTrue(BlockToolMatcher.canHarvest(Arrays.asList("voxel:ruby_drill"), null,
                "DIAMOND_HOE", "voxel:ruby_drill"));
        assertFalse(BlockToolMatcher.canHarvest(Arrays.asList("voxel:ruby_drill"), null,
                "DIAMOND_HOE", "voxel:other_drill"));
    }
}
