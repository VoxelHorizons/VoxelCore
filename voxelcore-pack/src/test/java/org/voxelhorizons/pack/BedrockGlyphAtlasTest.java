package org.voxelhorizons.pack;

import org.junit.Test;

import java.awt.image.BufferedImage;

import static org.junit.Assert.*;

public class BedrockGlyphAtlasTest {
    /** 43x11 two-colour "pixel art" with single-pixel detail (like a rank tag). */
    private static BufferedImage tag(int scale) {
        BufferedImage image = new BufferedImage(43 * scale, 11 * scale, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            image.setRGB(x, y, ((x / scale + y / scale) % 2 == 0) ? 0xff1060a0 : 0xffffffff);
        return image;
    }

    @Test public void nativePixelArtNearItsJavaHeightIsKeptCrisp() {
        BufferedImage out = BedrockGlyphAtlas.atJavaSize(tag(1), 9);
        assertEquals(43, out.getWidth());
        assertEquals(11, out.getHeight());
        assertEquals("No smoothing: pixels unchanged", 0xffffffff, out.getRGB(1, 0));
    }

    @Test public void integerUpscaledArtIsReducedExactly() {
        BufferedImage out = BedrockGlyphAtlas.atJavaSize(tag(4), 9);
        assertEquals(43, out.getWidth());
        assertEquals(11, out.getHeight());
        assertEquals(0xff1060a0, out.getRGB(0, 0));
        assertEquals(0xffffffff, out.getRGB(1, 0));
    }

    @Test public void smallArtGrowsByWholeMultiples() {
        BufferedImage icon = new BufferedImage(5, 4, BufferedImage.TYPE_INT_ARGB);
        icon.setRGB(0, 0, 0xffff0000);
        BufferedImage out = BedrockGlyphAtlas.atJavaSize(icon, 9);
        assertEquals("floor(9/4) = 2", 10, out.getWidth());
        assertEquals(8, out.getHeight());
    }
}
