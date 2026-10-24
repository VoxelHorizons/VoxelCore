package org.voxelhorizons.text;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class BedrockTooltipTextTest {
    @Test public void joinsReadableLinesWithoutGlyphs() {
        assertEquals("§eOak Chair" + BedrockTooltipText.SEPARATOR + "§7Sit: right click",
                BedrockTooltipText.subtitle("§eOak  Chair", "§7Sit: right click", "§f "));
        assertEquals("", BedrockTooltipText.subtitle(null, "", ""));
    }
}
