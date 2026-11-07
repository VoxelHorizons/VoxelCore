package org.voxelhorizons.pack;

import org.junit.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import static org.junit.Assert.*;

public class BedrockPaletteIconsTest {
    private static byte[] encode(BufferedImage image) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", bytes));
        return bytes.toByteArray();
    }

    @Test public void indexedPngIsPixelIdenticalAndNoLargerThanOriginal() throws Exception {
        BufferedImage source = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        int[] colors = {0x00000000, 0xFFFFFFFF, 0xFFCC6A28, 0xFF999999};
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++)
            source.setRGB(x, y, colors[(x / 8 + y / 8) % colors.length]);
        byte[] raw = encode(source);
        byte[] optimized = BedrockPaletteIcons.indexPngIfSmaller(raw);
        assertTrue("Generated swatch PNG must be no larger", optimized.length <= raw.length);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(optimized));
        assertNotNull(decoded);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++)
            assertEquals("Lossless PNG transcode at " + x + "," + y,
                    source.getRGB(x, y), decoded.getRGB(x, y));
    }

    @Test public void complexFullColourIconsAreNeverQuantized() throws Exception {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++)
            image.setRGB(x, y, 0xFF000000 | ((x * 20 + y) << 8) | x);
        byte[] encoded = encode(image);
        assertArrayEquals("Over 256 colours: do not change or quantize source PNG",
                encoded, BedrockPaletteIcons.indexPngIfSmaller(encoded));
    }
}
