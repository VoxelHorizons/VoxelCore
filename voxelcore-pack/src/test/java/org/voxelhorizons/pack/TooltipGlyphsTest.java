package org.voxelhorizons.pack;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TooltipGlyphsTest {
    @Test
    public void mapsThreeAsciiRowsToDistinctReservedGlyphs() {
        assertEquals((char) (TooltipGlyphs.LINE_1_BASE + 'A'), TooltipGlyphs.translateLine("A", 1).charAt(0));
        assertEquals((char) (TooltipGlyphs.LINE_2_BASE + 'A'), TooltipGlyphs.translateLine("A", 2).charAt(0));
        assertEquals((char) (TooltipGlyphs.LINE_3_BASE + 'A'), TooltipGlyphs.translateLine("A", 3).charAt(0));
        assertTrue(TooltipGlyphs.reservedCodePoints().contains(Integer.valueOf(TooltipGlyphs.LINE_1_BASE + 'A')));
    }

    @Test
    public void measuresAsciiAndIgnoresLegacyFormatting() {
        assertEquals(TooltipGlyphs.width("Hello"), TooltipGlyphs.width("\u00A7fHello"));
        assertEquals(4, TooltipGlyphs.width(" "));
        assertTrue(TooltipGlyphs.width("Tiki Coffee Table") > 0);
    }

    @Test
    public void backgroundSupportsEvenAndOddPixelWidths() {
        String even = TooltipGlyphs.backgroundForWidth(40);
        String odd = TooltipGlyphs.backgroundForWidth(41);
        assertEquals(40, TooltipGlyphs.backgroundWidth(even));
        assertEquals(41, TooltipGlyphs.backgroundWidth(odd));
        assertEquals((char) TooltipGlyphs.BACKGROUND_RIGHT_OFFSET, odd.charAt(odd.length() - 1));
    }

    @Test
    public void composeUsesAllThreeVerticalRowsAndStableSpacing() {
        String composed = TooltipGlyphs.compose(
                "\u00A7fTiki Coffee Table",
                "\u00A77Price: 250",
                "\u00A76(Click to purchase)",
                TooltipGlyphs.DEFAULT_X_OFFSET);
        assertTrue(composed.indexOf((char) TooltipGlyphs.BACKGROUND_LEFT) >= 0);
        assertTrue(composed.indexOf((char) (TooltipGlyphs.LINE_1_BASE + 'T')) >= 0);
        assertTrue(composed.indexOf((char) (TooltipGlyphs.LINE_2_BASE + 'P')) >= 0);
        assertTrue(composed.indexOf((char) (TooltipGlyphs.LINE_3_BASE + '(')) >= 0);
    }
}
