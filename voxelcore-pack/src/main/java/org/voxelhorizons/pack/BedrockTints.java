package org.voxelhorizons.pack;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Opt-in constant tints for Bedrock ({@code properties.bedrock.tints: {0: '#7fcc19'}}).
 * Java tints faces/layers at runtime from tint sources (dye, potion, grass...); Bedrock items
 * and our custom entities have no such source, so an author-chosen colour is baked into
 * the texture exactly as Java multiplies it: channel * tint / 255, alpha unchanged.
 */
final class BedrockTints {
    private BedrockTints() {}

    static Map<Integer, Integer> parse(Object raw, String itemId) throws IOException {
        if (raw == null) return Collections.emptyMap();
        if (!(raw instanceof Map)) throw new IOException("properties.bedrock.tints must be a map for " + itemId);
        Map<Integer, Integer> tints = new TreeMap<Integer, Integer>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            int index;
            try { index = Integer.parseInt(String.valueOf(entry.getKey())); }
            catch (NumberFormatException invalid) { throw new IOException("Tint index must be an integer for " + itemId); }
            if (index < 0 || index > 31) throw new IOException("Tint index out of range for " + itemId);
            String value = String.valueOf(entry.getValue());
            if (!value.matches("#[0-9a-fA-F]{6}")) throw new IOException("Tint must be #rrggbb for " + itemId);
            tints.put(Integer.valueOf(index), Integer.valueOf(Integer.parseInt(value.substring(1), 16)));
        }
        return Collections.unmodifiableMap(tints);
    }

    static BufferedImage apply(BufferedImage source, int rgb) {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int tr = (rgb >> 16) & 255, tg = (rgb >> 8) & 255, tb = rgb & 255;
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
            int argb = source.getRGB(x, y);
            int r = ((argb >> 16) & 255) * tr / 255, g = ((argb >> 8) & 255) * tg / 255, b = (argb & 255) * tb / 255;
            out.setRGB(x, y, (argb & 0xff000000) | (r << 16) | (g << 8) | b);
        }
        return out;
    }

    /** Tinted copy for a face with {@code tintindex}, or an error when no tint was configured. */
    static BufferedImage face(BufferedImage source, int tintIndex, Map<Integer, Integer> tints, String model) throws IOException {
        Integer rgb = tints.get(Integer.valueOf(tintIndex));
        if (rgb == null)
            throw new IOException("Tinted model needs properties.bedrock.tints." + tintIndex + " or an explicit Bedrock texture: " + model);
        return apply(source, rgb.intValue());
    }
}
