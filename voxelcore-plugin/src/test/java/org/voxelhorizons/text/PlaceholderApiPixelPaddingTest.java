package org.voxelhorizons.text;

import org.junit.Test;
import org.voxelhorizons.pack.UiSpacingGlyphs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PlaceholderApiPixelPaddingTest {
    @Test
    public void defaultFontCharactersHaveActualPixelWidths() {
        assertEquals(4, MinecraftTextPixelWidth.width(" "));
        assertEquals(6, MinecraftTextPixelWidth.width("A"));
        assertEquals(2, MinecraftTextPixelWidth.width("i"));
        assertEquals(3, MinecraftTextPixelWidth.width("l"));
        assertEquals(2, MinecraftTextPixelWidth.width("."));
        assertEquals(12, MinecraftTextPixelWidth.width("10"));
    }

    @Test
    public void legacyColoursAndBoldDoNotShiftAlignment() {
        assertEquals(MinecraftTextPixelWidth.width("Fibbicles"),
                MinecraftTextPixelWidth.width("\u00a7fFibbicles"));
        assertEquals(MinecraftTextPixelWidth.width("Fibbicles"),
                MinecraftTextPixelWidth.width("&eFibbicles"));
        assertEquals(7, MinecraftTextPixelWidth.width("&lA"));
        assertEquals(13, MinecraftTextPixelWidth.width("&lA&fA"));
        assertEquals(6, MinecraftTextPixelWidth.width("&#ff00ffA"));
    }

    @Test
    public void paddedRankAndNamesEndAtIdenticalPixelColumns() {
        String[] ranks = {"1.", "2.", "10."};
        String[] names = {"FlopsiNZ", "Fibbicles", "SwedishCheff", "Th3Nightbring3r", "&7Unknown"};
        for (String rank : ranks) {
            String rankPadded = PlaceholderApiPixelPadding.pad(rank, 28);
            assertEquals(28, MinecraftTextPixelWidth.width(rankPadded));
            for (String name : names) {
                String padded = PlaceholderApiPixelPadding.pad(name, 156);
                assertEquals(156, MinecraftTextPixelWidth.width(padded));
                assertEquals(184, MinecraftTextPixelWidth.width(rankPadded + padded));
            }
        }
    }

    @Test
    public void existingSpaceGlyphsAreReusedAndLongNamesAreNotTruncated() {
        String value = PlaceholderApiPixelPadding.pad("a", 100);
        assertTrue(value.length() > 1);
        assertEquals(100, MinecraftTextPixelWidth.width(value));
        assertEquals("Long Name", PlaceholderApiPixelPadding.pad("Long Name", 1));
        assertEquals(0, MinecraftTextPixelWidth.width(UiSpacingGlyphs.charactersForOffset(0)));
    }
}
