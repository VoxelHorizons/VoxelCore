package org.voxelhorizons.pack;

import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.*;

public class BedrockGlyphTilesTest {
    @Test public void replacesOnlyTiledGlyphs() throws IOException {
        Map<Character, String> map = BedrockGlyphTiles.parse("{\"schema\":1,\"tiles\":{\"E234\":[\"F400\",\"F401\"]}}");
        assertEquals("§f §dName: hi ",
                BedrockGlyphTiles.apply("§f §dName: hi ", map));
        String untouched = "plain text";
        assertSame(untouched, BedrockGlyphTiles.apply(untouched, map));
    }

    @Test(expected = IOException.class)
    public void rejectsTilesOutsideTheBedrockOnlyRange() throws IOException {
        BedrockGlyphTiles.parse("{\"schema\":1,\"tiles\":{\"E234\":[\"0041\"]}}");
    }
}
