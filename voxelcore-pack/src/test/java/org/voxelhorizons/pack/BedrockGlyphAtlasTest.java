package org.voxelhorizons.pack;

import org.junit.Test;

import java.awt.image.BufferedImage;
import java.util.Arrays;

import static org.junit.Assert.*;

public class BedrockGlyphAtlasTest {
    /** 43x11 two-colour "pixel art" with single-pixel detail (like a rank tag). */
    private static BufferedImage tag(int scale) {
        BufferedImage image = new BufferedImage(43 * scale, 11 * scale, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            image.setRGB(x, y, ((x / scale + y / scale) % 2 == 0) ? 0xff1060a0 : 0xffffffff);
        return image;
    }

    private static BufferedImage flag() {
        BufferedImage flag = new BufferedImage(24, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 24; x++) flag.setRGB(x, y, x < 12 ? 0xffc0102e : 0xff00247d);
        return flag;
    }

    @Test public void tagsStayPixelExactAndFlagsMatchTheirHeight() {
        BedrockGlyphAtlas.Glyph tag = new BedrockGlyphAtlas.Glyph(tag(1), 9);
        BedrockGlyphAtlas.Glyph flag = new BedrockGlyphAtlas.Glyph(flag(), 9);
        double ratio = BedrockGlyphAtlas.pixelRatio(Arrays.asList(tag, flag, flag, flag));
        assertEquals("Ratio comes from the text tags even when flags outnumber them", 11.0 / 9, ratio, 1e-9);
        BufferedImage tagOut = BedrockGlyphAtlas.sized(tag.art, tag.javaHeight, ratio);
        assertSame("Tag untouched", tag.art, tagOut);
        BufferedImage flagOut = BedrockGlyphAtlas.sized(flag.art, flag.javaHeight, ratio);
        assertEquals("Same Java height -> same Bedrock height as the tag", 11, flagOut.getHeight());
        assertEquals("Solid areas keep their exact colour", 0xffc0102e, flagOut.getRGB(0, 5));
        assertEquals(0xff00247d, flagOut.getRGB(flagOut.getWidth() - 1, 5));
    }

    @Test public void flagsShrinkWhenJavaDrawsTagsLargerThanNative() {
        // Live test: 11px tag art at Java height 18, 16px flag at Java height 16 (Java shows the
        // flag ~0.9x the tag). Bedrock must keep the tag native and draw the flag ~10px.
        BedrockGlyphAtlas.Glyph tag = new BedrockGlyphAtlas.Glyph(tag(1), 18);
        BedrockGlyphAtlas.Glyph flag = new BedrockGlyphAtlas.Glyph(flag(), 16);
        double ratio = BedrockGlyphAtlas.pixelRatio(Arrays.asList(tag, flag));
        assertEquals(11.0 / 18, ratio, 1e-9);
        assertSame(tag.art, BedrockGlyphAtlas.sized(tag.art, tag.javaHeight, ratio));
        assertEquals(10, BedrockGlyphAtlas.sized(flag.art, flag.javaHeight, ratio).getHeight());
    }

    @Test public void shrinkingKeepsThinDetailAndTransparentEdgesAreNotDarkened() {
        BufferedImage star = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) star.setRGB(x, y, 0xff00247d);
        star.setRGB(5, 5, 0xffffffff); // 1px star: nearest 16->10 would sample past it
        BufferedImage out = BedrockGlyphAtlas.areaAverage(star, 10, 10);
        boolean lighter = false;
        for (int y = 0; y < 10; y++) for (int x = 0; x < 10; x++) lighter |= ((out.getRGB(x, y) >> 16) & 255) > 0x20;
        assertTrue("The star still shows", lighter);
        BufferedImage edge = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        edge.setRGB(0, 0, 0xffffffff); // white next to transparent black
        assertEquals("Colour not darkened by the transparent neighbour", 0xffffff,
                BedrockGlyphAtlas.areaAverage(edge, 1, 1).getRGB(0, 0) & 0xffffff);
    }

    @Test public void integerUpscaledArtIsReducedExactly() {
        BufferedImage out = BedrockGlyphAtlas.reduceBlocks(tag(4));
        assertEquals(43, out.getWidth());
        assertEquals(11, out.getHeight());
        assertEquals(0xff1060a0, out.getRGB(0, 0));
        assertEquals(0xffffffff, out.getRGB(1, 0));
    }

    @Test public void smallArtGrowsByWholeMultiples() {
        BufferedImage icon = new BufferedImage(5, 4, BufferedImage.TYPE_INT_ARGB);
        icon.setRGB(0, 0, 0xffff0000);
        BufferedImage out = BedrockGlyphAtlas.sized(icon, 8, 1.0);
        assertEquals(10, out.getWidth());
        assertEquals(8, out.getHeight());
    }
}
