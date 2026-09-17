package org.voxelhorizons.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PackAssetOptimizerTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void minifiesJsonWithoutChangingStringContent() {
        String source = "{\n  \"label\": \"A value with spaces and \\\"quotes\\\"\",\n"
                + "  \"glyph\": \"\\uEF00\",\n  \"array\": [1, 2, true]\n}\n";
        byte[] minified = PackAssetOptimizer.minifyJson(source.getBytes(StandardCharsets.UTF_8), "test.json");
        assertEquals("{\"label\":\"A value with spaces and \\\"quotes\\\"\",\"glyph\":\"\\uEF00\","
                + "\"array\":[1,2,true]}", new String(minified, StandardCharsets.UTF_8));
    }

    @Test
    public void losslessPngOptimizationNeverIncreasesSize() throws Exception {
        BufferedImage source = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.setRGB(x, y, ((x + y) % 2 == 0) ? 0xFFFF0000 : 0x00000000);
            }
        }
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(source, "png", encoded));
        byte[] bloated = Arrays.copyOf(encoded.toByteArray(), encoded.size() + 2048);

        byte[] optimized = PackAssetOptimizer.optimizePng(bloated);
        assertTrue(optimized.length < bloated.length);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(optimized));
        assertEquals(source.getWidth(), decoded.getWidth());
        assertEquals(source.getHeight(), decoded.getHeight());
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) assertEquals(source.getRGB(x, y), decoded.getRGB(x, y));
        }

        byte[] alreadySmall = encoded.toByteArray();
        assertTrue(PackAssetOptimizer.optimizePng(alreadySmall).length <= alreadySmall.length);
    }

    @Test
    public void writesMinifiedCompressedAndReproducibleZip() throws Exception {
        TreeMap<String, byte[]> entries = new TreeMap<String, byte[]>();
        entries.put("assets/test/lang/en_us.json",
                ("{\n  \"long\": \"" + repeat("voxelcore ", 100) + "\"\n}\n").getBytes(StandardCharsets.UTF_8));
        entries.put("assets/test/data.bin", new byte[] { 1, 9, 4, 7, 2, 8, 3, 6 });

        Path first = temporaryFolder.newFile("first.zip").toPath();
        Path second = temporaryFolder.newFile("second.zip").toPath();
        DeterministicZipWriter.write(first, entries);
        DeterministicZipWriter.write(second, entries);
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));

        ZipFile zip = new ZipFile(first.toFile());
        try {
            ZipEntry json = zip.getEntry("assets/test/lang/en_us.json");
            assertEquals(ZipEntry.DEFLATED, json.getMethod());
            String value = new String(read(zip, json), StandardCharsets.UTF_8);
            assertTrue(!value.contains("\n"));
            assertEquals(ZipEntry.STORED, zip.getEntry("assets/test/data.bin").getMethod());
        } finally {
            zip.close();
        }
    }

    private static byte[] read(ZipFile zip, ZipEntry entry) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        java.io.InputStream input = zip.getInputStream(entry);
        try {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static String repeat(String value, int count) {
        StringBuilder output = new StringBuilder();
        for (int index = 0; index < count; index++) output.append(value);
        return output.toString();
    }
}
