package org.voxelhorizons.pack;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generated palette icon cache, shared across all dyeable Java models.
 *
 * Output names are hashes of actual PNG content: identical 2D icons reuse
 * ONE texture file AND ONE Bedrock atlas entry, even when multiple Java
 * items/models or palette shades produce identical pixels.
 *
 * This class only adds icon assets after checking the unique-PNG byte budget.
 * Mapping-count limits are enforced by BedrockPackCompiler separately.
 */
final class BedrockPaletteIcons {
    private final BedrockIconRenderer renderer;
    private final Map<String, byte[]> assets;
    private final JsonObject textures;
    private final Map<String, byte[]> rendered = new HashMap<String, byte[]>();
    private final Path cache;
    private final byte[] sourceDigest;
    private final long maxBytes;
    private long bytes;
    private int uniqueTextures;

    BedrockPaletteIcons(BedrockIconRenderer renderer, Map<String, byte[]> assets,
                        JsonObject textures, Path outputRoot, byte[] sourceDigest, long maxBytes) {
        if (maxBytes < 1) throw new IllegalArgumentException("Bedrock palette texture budget must be positive");
        this.renderer = renderer;
        this.assets = assets;
        this.textures = textures;
        this.cache = outputRoot.resolve("icon-cache");
        this.sourceDigest = sourceDigest.clone();
        this.maxBytes = maxBytes;
    }

    /**
     * @return reusable atlas icon key, or null when the configured byte budget
     *         would be exceeded. The caller can keep the default Bedrock map.
     */
    String icon(String model, double[] camera, Map<Integer, Integer> tints,
                byte[] defaultPng) throws IOException {
        String renderKey = model + "|" + Arrays.toString(camera) + "|" + tints;
        byte[] png = rendered.get(renderKey);
        if (png == null) {
            byte[] digest = sha("bedrock-palette-render-v1:" + renderKey, sourceDigest);
            Path file = cache.resolve(hex(digest) + ".png");
            if (Files.isRegularFile(file)) png = Files.readAllBytes(file);
            else {
                png = renderer.render(model, camera, tints);
                Files.createDirectories(file.getParent());
                Files.write(file, png);
            }
            png = indexPngIfSmaller(png);
            rendered.put(renderKey, png);
        }
        // No additional mapping is useful when the RGB shade produces
        // exactly the same visible pixels as the undyed/default item.
        if (Arrays.equals(png, defaultPng)) return "";
        String hash = hex(sha(png));
        String suffix = hash.substring(0, 32);
        String icon = "voxelcore.p_" + suffix;
        String path = "textures/voxelcore/p_" + suffix + ".png";
        byte[] previous = assets.get(path);
        if (previous == null) {
            if (bytes > maxBytes - png.length) return null;
            assets.put(path, png);
            bytes += png.length;
            uniqueTextures++;
        } else if (!Arrays.equals(previous, png)) {
            throw new IOException("Bedrock palette hash collision for " + path);
        }
        if (!textures.has(icon)) {
            JsonObject tex = new JsonObject();
            tex.addProperty("textures", path.substring(0, path.length() - ".png".length()));
            textures.add(icon, tex);
        }
        return icon;
    }

    long uniqueBytes() { return bytes; }
    int uniqueTextures() { return uniqueTextures; }

    /**
     * Losslessly encode simple swatch and low-colour furniture icons as indexed
     * PNGs when possible. Indexed PNGs are substantially smaller than RGBA
     * for the 16x16 / 18x18 picker art; complex 3D icons with >256 distinct
     * colours keep their original full-colour PNGs.
     */
    static byte[] indexPngIfSmaller(byte[] original) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
            if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0)
                return original;
            Map<Integer, Integer> palette = new LinkedHashMap<Integer, Integer>();
            for (int y = 0; y < source.getHeight(); y++)
                for (int x = 0; x < source.getWidth(); x++) {
                    Integer argb = Integer.valueOf(source.getRGB(x, y));
                    if (palette.containsKey(argb)) continue;
                    if (palette.size() >= 256) return original;
                    palette.put(argb, Integer.valueOf(palette.size()));
                }
            byte[] reds = new byte[palette.size()], greens = new byte[palette.size()];
            byte[] blues = new byte[palette.size()], alphas = new byte[palette.size()];
            for (Map.Entry<Integer,Integer> e : palette.entrySet()) {
                int rgb = e.getKey().intValue(), idx = e.getValue().intValue();
                reds[idx] = (byte)(rgb >>> 16);
                greens[idx] = (byte)(rgb >>> 8);
                blues[idx] = (byte)rgb;
                alphas[idx] = (byte)(rgb >>> 24);
            }
            IndexColorModel indexedModel = new IndexColorModel(8, palette.size(),
                    reds, greens, blues, alphas);
            WritableRaster pixels = indexedModel.createCompatibleWritableRaster(
                    source.getWidth(), source.getHeight());
            for (int y = 0; y < source.getHeight(); y++)
                for (int x = 0; x < source.getWidth(); x++)
                    pixels.setSample(x, y, 0,
                            palette.get(Integer.valueOf(source.getRGB(x, y))).intValue());
            BufferedImage indexed = new BufferedImage(indexedModel, pixels, false, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(indexed, "png", out)) return original;
            byte[] optimized = out.toByteArray();
            return optimized.length < original.length ? optimized : original;
        } catch (IOException | RuntimeException unsupported) {
            return original;
        }
    }

    static byte[] sha(byte[] source) {
        try { return MessageDigest.getInstance("SHA-256").digest(source); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    }

    private static byte[] sha(String value, byte[] digest) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(value.getBytes(StandardCharsets.UTF_8));
            sha.update(digest);
            return sha.digest();
        } catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    }

    private static String hex(byte[] raw) {
        char[] result = new char[raw.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < raw.length; i++) {
            result[i * 2] = digits[(raw[i] >> 4) & 15];
            result[i * 2 + 1] = digits[raw[i] & 15];
        }
        return new String(result);
    }
}
