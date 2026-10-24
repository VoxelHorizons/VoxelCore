package org.voxelhorizons.pack;

import org.voxelhorizons.content.ContentID;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * Converts small managed Java inline bitmap glyphs to Bedrock native glyph pages.
 * Bedrock's vanilla glyph_E0 and glyph_E1 are never touched. This deliberately does
 * not translate Java GUI backgrounds, custom negative-spacing or text transforms.
 */
final class BedrockGlyphAtlas {
    private BedrockGlyphAtlas() {}

    static int append(Path contentRoot, Path allocationManifest, Map<String, byte[]> javaAssets,
                      Map<String, byte[]> bedrockAssets) throws IOException {
        UiGlyphRegistry glyphs = new UiGlyphLoader().load(contentRoot, allocationManifest, false);
        Map<Integer, BufferedImage> sheets = new TreeMap<Integer, BufferedImage>();
        int count = 0;
        for (UiGlyphDefinition glyph : glyphs.entries().values()) {
            if (glyph.gui() || glyph.scaleRatio() > 32) continue;
            int codePoint = glyph.codePoint();
            if (codePoint < 0xE200 || codePoint > 0xF3FF) continue;
            String[] ref = glyph.texture().split(":", 2);
            byte[] source = javaAssets.get("assets/" + ref[0] + "/textures/" + ref[1] + ".png");
            if (source == null) throw new IOException("Missing Java bitmap for Bedrock glyph " + glyph.id());
            BufferedImage icon = ImageIO.read(new ByteArrayInputStream(source));
            if (icon == null || icon.getWidth() < 1 || icon.getHeight() < 1) {
                throw new IOException("Invalid PNG for Bedrock glyph " + glyph.id());
            }
            // Keep the native sheet limited to small inline icons. Wide pictures and
            // fullscreen GUI overlays are incompatible with 16x16 Bedrock font tiles.
            if (icon.getWidth() > 32 || icon.getHeight() > 32) continue;
            int page = codePoint >>> 8;
            String name = String.format(java.util.Locale.ROOT, "font/glyph_%02X.png", page);
            BufferedImage sheet = sheets.get(page);
            if (sheet == null) {
                byte[] authored = bedrockAssets.get(name);
                if (authored != null) {
                    sheet = ImageIO.read(new ByteArrayInputStream(authored));
                    if (sheet == null || sheet.getWidth() != sheet.getHeight()
                            || sheet.getWidth() % 16 != 0 || sheet.getWidth() < 256)
                        throw new IOException("Invalid authored Bedrock glyph atlas " + name);
                } else sheet = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
                sheets.put(page, sheet);
            }
            int cell = sheet.getWidth() / 16;
            int slot = codePoint & 255, x = (slot % 16) * cell, y = (slot / 16) * cell;
            for (int yy=y; yy<y+cell; yy++) for(int xx=x;xx<x+cell;xx++) {
                if ((sheet.getRGB(xx, yy) >>> 24) != 0)
                    throw new IOException("Bedrock font cell U+" + Integer.toHexString(codePoint).toUpperCase(java.util.Locale.ROOT)
                            + " is already occupied in " + name + " (managed glyph " + glyph.id() + ")");
            }
            double ratio = Math.min((double)cell/icon.getWidth(), (double)cell/icon.getHeight());
            int width = Math.max(1,(int)Math.round(icon.getWidth()*ratio));
            int height = Math.max(1,(int)Math.round(icon.getHeight()*ratio));
            Graphics2D graphics = sheet.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                graphics.drawImage(icon,x+(cell-width)/2,y+(cell-height)/2,width,height,null);
            } finally { graphics.dispose(); }
            count++;
        }
        for (Map.Entry<Integer,BufferedImage> entry : sheets.entrySet()) {
            String name = String.format(java.util.Locale.ROOT, "font/glyph_%02X.png", entry.getKey());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(entry.getValue(), "png", output)) throw new IOException("PNG encoding unavailable");
            bedrockAssets.put(name, output.toByteArray());
        }
        return count;
    }
}
