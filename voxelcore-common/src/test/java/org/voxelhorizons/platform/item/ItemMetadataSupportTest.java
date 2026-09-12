package org.voxelhorizons.platform.item;

import org.bukkit.inventory.ItemFlag;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ItemMetadataSupportTest {

    @Test
    public void resolvesBukkitEnumNamesWithoutHardCodedBranches() {
        assertEquals(ItemFlag.HIDE_ATTRIBUTES, ItemMetadataSupport.resolveFlag("hide_attributes"));
        assertEquals(ItemFlag.HIDE_UNBREAKABLE, ItemMetadataSupport.resolveFlag("HIDE_UNBREAKABLE"));
        assertEquals(ItemFlag.HIDE_DYE, ItemMetadataSupport.resolveFlag("hide-dye"));
    }

    @Test
    public void resolvesFriendlyLegacyAliasesWhenDirectNameIsUnavailable() {
        assertEquals(ItemFlag.HIDE_ENCHANTS, ItemMetadataSupport.resolveFlag("hide_enchantments"));
        assertEquals(ItemFlag.HIDE_DESTROYS, ItemMetadataSupport.resolveFlag("hide_destroyable"));
        assertEquals(ItemFlag.HIDE_PLACED_ON, ItemMetadataSupport.resolveFlag("hide_placeable"));
    }

    @Test
    public void ignoresUnknownOrEmptyFlags() {
        assertNull(ItemMetadataSupport.resolveFlag("hide_definitely_not_a_real_flag"));
        assertNull(ItemMetadataSupport.resolveFlag("  "));
        assertNull(ItemMetadataSupport.resolveFlag(null));
    }
}
