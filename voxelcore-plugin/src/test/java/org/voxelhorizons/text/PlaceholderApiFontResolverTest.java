package org.voxelhorizons.text;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PlaceholderApiFontResolverTest {
    @Test
    public void mapsUniqueAndNamespacedFontParametersToAliases() {
        assertEquals(":staff:", PlaceholderApiFontResolver.alias("font_staff"));
        assertEquals(":staff_badge:", PlaceholderApiFontResolver.alias("FONT_staff_badge"));
        assertEquals(":voxel/staff:", PlaceholderApiFontResolver.alias("font_voxel/staff"));
    }

    @Test
    public void rejectsUnrelatedOrUnsafeParameters() {
        assertNull(PlaceholderApiFontResolver.alias("player_name"));
        assertNull(PlaceholderApiFontResolver.alias("font_"));
        assertNull(PlaceholderApiFontResolver.alias("font_voxel/staff/extra"));
        assertNull(PlaceholderApiFontResolver.alias("font_../staff"));
    }
}
