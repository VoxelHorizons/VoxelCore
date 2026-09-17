package org.voxelhorizons.pack;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;

/** Lossless per-entry optimizations applied immediately before pack publication. */
final class PackAssetOptimizer {
    private static final byte[] PNG_SIGNATURE = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private PackAssetOptimizer() {}

    static byte[] optimize(String path, byte[] content) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".json") || lower.endsWith(".mcmeta")) return minifyJson(content, path);
        if (lower.endsWith(".png")) return optimizePng(content);
        return content;
    }

    static byte[] minifyJson(byte[] content, String path) {
        String input = new String(content, StandardCharsets.UTF_8);
        StringBuilder output = new StringBuilder(input.length());
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < input.length(); index++) {
            char character = input.charAt(index);
            if (quoted) {
                output.append(character);
                if (escaped) escaped = false;
                else if (character == '\\') escaped = true;
                else if (character == '"') quoted = false;
            } else if (character == '"') {
                quoted = true;
                output.append(character);
            } else if (character != ' ' && character != '\t' && character != '\r' && character != '\n') {
                output.append(character);
            }
        }
        if (quoted || escaped) throw new JavaPackCompileException("Unterminated JSON string in " + path);
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    static byte[] optimizePng(byte[] content) {
        if (!hasPngSignature(content)) return content;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) return content;
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
            if (!writers.hasNext()) return content;
            ImageWriter writer = writers.next();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(content.length);
            try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
                writer.setOutput(output);
                ImageWriteParam parameters = writer.getDefaultWriteParam();
                if (parameters.canWriteCompressed()) {
                    parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    String[] types = parameters.getCompressionTypes();
                    if (types != null && types.length > 0) parameters.setCompressionType(types[0]);
                    parameters.setCompressionQuality(0.0F);
                }
                writer.write(null, new IIOImage(image, null, null), parameters);
            } finally {
                writer.dispose();
            }
            byte[] optimized = bytes.toByteArray();
            return optimized.length < content.length ? optimized : content;
        } catch (IOException | RuntimeException ignored) {
            // Optimization must never make an otherwise usable authored asset unbuildable.
            return content;
        }
    }

    private static boolean hasPngSignature(byte[] content) {
        if (content.length < PNG_SIGNATURE.length) return false;
        for (int index = 0; index < PNG_SIGNATURE.length; index++) {
            if (content[index] != PNG_SIGNATURE[index]) return false;
        }
        return true;
    }
}
