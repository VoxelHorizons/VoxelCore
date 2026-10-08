package org.voxelhorizons.pack;

import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TooltipGlyphsTest {
    @Test public void mapsThreeAsciiRowsToDistinctReservedGlyphs() {
        assertEquals((char) (TooltipGlyphs.LINE_1_BASE + 'A'), TooltipGlyphs.translateLine("A", 1).charAt(0));
        assertEquals((char) (TooltipGlyphs.LINE_2_BASE + 'A'), TooltipGlyphs.translateLine("A", 2).charAt(0));
        assertEquals((char) (TooltipGlyphs.LINE_3_BASE + 'A'), TooltipGlyphs.translateLine("A", 3).charAt(0));
        assertTrue(TooltipGlyphs.reservedCodePoints().contains(Integer.valueOf(TooltipGlyphs.LINE_1_BASE + 'A')));
    }

    @Test public void measuresAsciiAndIgnoresLegacyFormatting() {
        assertEquals(TooltipGlyphs.width("Hello"), TooltipGlyphs.width("\u00A7fHello"));
        assertEquals(4, TooltipGlyphs.width(" "));
        assertEquals(4, TooltipGlyphs.lineAscent(1));
        assertEquals(0, TooltipGlyphs.lineAscent(2));
        assertEquals(-4, TooltipGlyphs.lineAscent(3));
    }

    @Test public void layoutUsesUiGlyphsAndRemovesTileSeams() {
        Map<ContentID, UiGlyphDefinition> definitions = new LinkedHashMap<ContentID, UiGlyphDefinition>();
        ContentID left = ContentID.of("voxel", "tooltip_left");
        ContentID center = ContentID.of("voxel", "tooltip_center");
        ContentID right = ContentID.of("voxel", "tooltip_right");
        definitions.put(left, new UiGlyphDefinition(left, "voxel:ui/tooltip/left", 19, 7, 2, false, 0xE110));
        definitions.put(center, new UiGlyphDefinition(center, "voxel:ui/tooltip/center", 19, 7, 2, false, 0xE111));
        definitions.put(right, new UiGlyphDefinition(right, "voxel:ui/tooltip/right", 19, 7, 2, false, 0xE112));
        TooltipLayout layout = new TooltipLayout(new UiGlyphRegistry(definitions),
                "voxel:tooltip_left", "voxel:tooltip_center", "voxel:tooltip_right", 1, 4, 55);
        String composed = layout.compose("This is a test", "Second Test", "Third");
        assertTrue(composed.contains(String.valueOf((char) 0xE110)));
        assertTrue(composed.contains(String.valueOf((char) 0xE111)));
        assertTrue(composed.contains(String.valueOf((char) 0xE112)));
        assertTrue(composed.contains(UiSpacingGlyphs.charactersForOffset(-1)));
        assertTrue(composed.indexOf((char) (TooltipGlyphs.LINE_1_BASE + 'T')) >= 0);
        assertTrue(composed.indexOf((char) (TooltipGlyphs.LINE_2_BASE + 'S')) >= 0);
        assertTrue(composed.indexOf((char) (TooltipGlyphs.LINE_3_BASE + 'T')) >= 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingConfiguredGlyphIsRejected() {
        new TooltipLayout(new UiGlyphRegistry(new LinkedHashMap<ContentID, UiGlyphDefinition>()),
                "voxel:missing", "voxel:missing", "voxel:missing", 1, 4, 55);
    }
    @Test public void crosshairAnchorUsesHalfDynamicBoxWidth() {
        Map<ContentID, UiGlyphDefinition> definitions = new LinkedHashMap<ContentID, UiGlyphDefinition>();
        for (String name : new String[]{"left", "center", "right"}) {
            ContentID id = ContentID.of("voxel", "tooltip_" + name);
            definitions.put(id, new UiGlyphDefinition(id, "voxel:ui/tooltip/" + name, 19, 7, 2, false,
                    0xE100 + definitions.size()));
        }
        UiGlyphRegistry glyphs = new UiGlyphRegistry(definitions);
        TooltipLayout centered = new TooltipLayout(glyphs,
                "voxel:tooltip_left", "voxel:tooltip_center", "voxel:tooltip_right", 1, 4, 8, false);
        TooltipLayout crosshair = new TooltipLayout(glyphs,
                "voxel:tooltip_left", "voxel:tooltip_center", "voxel:tooltip_right", 1, 4, 8, true);

        String shortCentered = centered.compose("Hi", "", "");
        String shortCrosshair = crosshair.compose("Hi", "", "");
        String longCentered = centered.compose("A longer tooltip title", "", "");
        String longCrosshair = crosshair.compose("A longer tooltip title", "", "");
        int shortWidth = TooltipGlyphs.width("Hi") + 8;
        int longWidth = TooltipGlyphs.width("A longer tooltip title") + 8;
        assertTrue(shortCrosshair.startsWith(UiSpacingGlyphs.charactersForOffset(8 + (shortWidth + 1) / 2)));
        assertTrue(longCrosshair.startsWith(UiSpacingGlyphs.charactersForOffset(8 + (longWidth + 1) / 2)));
        assertTrue(shortCentered.startsWith(UiSpacingGlyphs.charactersForOffset(8)));
        assertTrue(longCentered.startsWith(UiSpacingGlyphs.charactersForOffset(8)));
    }

}
