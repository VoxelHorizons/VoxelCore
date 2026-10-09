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
        assertEquals(-1, TooltipGlyphs.lineAscent(2));
        assertEquals(-6, TooltipGlyphs.lineAscent(3));
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
    @Test public void horizontalSpacingPreservesFormattingAndMeasuresAdjustedWidth() {
        String input = "AB \u00A7cCD";
        String baseline = TooltipGlyphs.translateLine(input, 1);
        String compact = TooltipGlyphs.translateLine(input, 1, -1, -1);
        String renderedSpacing = UiSpacingGlyphs.charactersForOffset(-1);
        assertTrue(compact.contains(renderedSpacing));
        assertTrue(compact.contains("\u00A7c"));
        assertEquals(TooltipGlyphs.width(input) - 3,
                TooltipGlyphs.width(input, -1, -1));
        assertEquals(TooltipGlyphs.width("A"),
                TooltipGlyphs.width("A", -1, -1)); // No leading kerning.
        assertEquals(TooltipGlyphs.width("A B"),
                TooltipGlyphs.width("A B", -1, 0)); // Only within words.
        assertTrue(compact.length() > baseline.length());
    }

    @Test public void customIconAndColorCodesDoNotCreateArtificialLetterKerning() {
        String input = "A\uE010B\u00A7fC";
        String compact = TooltipGlyphs.translateLine(input, 1, -1, -1);
        assertTrue(compact.contains("\uE010"));
        assertTrue(compact.contains("\u00A7f"));
        assertEquals(TooltipGlyphs.width(input) - 1,
                TooltipGlyphs.width(input, -1, -1)); // Only B-to-C.
    }

    @Test public void acceptsArbitraryNegativeLetterAndWordSpacing() {
        Map<ContentID, UiGlyphDefinition> definitions = new LinkedHashMap<ContentID, UiGlyphDefinition>();
        for (String name : new String[]{"left", "center", "right"}) {
            ContentID id = ContentID.of("voxel", "tooltip_" + name);
            definitions.put(id, new UiGlyphDefinition(id, "voxel:ui/tooltip/" + name,
                    19, 7, 2, false, 0xE100 + definitions.size()));
        }
        UiGlyphRegistry glyphs = new UiGlyphRegistry(definitions);
        TooltipLayout compact = new TooltipLayout(glyphs,
                "voxel:tooltip_left", "voxel:tooltip_center", "voxel:tooltip_right",
                1, 4, 30, -6, -20);
        String output = compact.compose("Text with wide spaces", "Second line", "Third");
        assertTrue(output.contains(UiSpacingGlyphs.charactersForOffset(-6)));
        assertTrue(output.contains(UiSpacingGlyphs.charactersForOffset(-20)));
        assertEquals(TooltipGlyphs.width("A B") - 20, TooltipGlyphs.width("A B", 0, -20));
        assertEquals(TooltipGlyphs.width("AB") - 6, TooltipGlyphs.width("AB", -6, 0));
    }

    @Test public void layoutAppliesHorizontalSpacingToBackgroundSizing() {
        Map<ContentID, UiGlyphDefinition> definitions = new LinkedHashMap<ContentID, UiGlyphDefinition>();
        for (String name : new String[]{"left", "center", "right"}) {
            ContentID id = ContentID.of("voxel", "tooltip_" + name);
            definitions.put(id, new UiGlyphDefinition(id, "voxel:ui/tooltip/" + name,
                    19, 7, 2, false, 0xE100 + definitions.size()));
        }
        UiGlyphRegistry glyphs = new UiGlyphRegistry(definitions);
        TooltipLayout loose = new TooltipLayout(glyphs,
                "voxel:tooltip_left", "voxel:tooltip_center", "voxel:tooltip_right",
                1, 4, 30, 0, 0);
        TooltipLayout compact = new TooltipLayout(glyphs,
                "voxel:tooltip_left", "voxel:tooltip_center", "voxel:tooltip_right",
                1, 4, 30, -1, -1);
        String looseText = loose.compose("AB CD", "", "");
        String compactText = compact.compose("AB CD", "", "");
        char centerCharacter = (char) 0xE101;
        assertTrue(compactText.chars().filter(c -> c == centerCharacter).count()
                < looseText.chars().filter(c -> c == centerCharacter).count());
        assertTrue(compactText.startsWith(UiSpacingGlyphs.charactersForOffset(30)));
    }

}
