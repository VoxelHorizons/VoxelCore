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
 * Converts small managed Java inline bitmap glyphs, and authored default-font bitmap
 * icons, to Bedrock native glyph pages.
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
            // A broken glyph bitmap is the Java build's error to report; never block Bedrock on it.
            if (source == null) continue;
            BufferedImage icon = ImageIO.read(new ByteArrayInputStream(source));
            if (icon == null || icon.getWidth() < 1 || icon.getHeight() < 1) continue;
            // Keep the native sheet limited to small inline icons. Wide pictures and
            // fullscreen GUI overlays are incompatible with 16x16 Bedrock font tiles.
            if (icon.getWidth() > 32 || icon.getHeight() > 32) continue;
            if (!place(sheets, bedrockAssets, codePoint, icon))
                throw new IOException("Bedrock font cell U+" + Integer.toHexString(codePoint).toUpperCase(java.util.Locale.ROOT)
                        + " is already occupied (managed glyph " + glyph.id() + ")");
            count++;
        }
        count += appendAuthored(contentRoot, javaAssets, sheets, bedrockAssets);
        for (Map.Entry<Integer,BufferedImage> entry : sheets.entrySet()) {
            String name = String.format(java.util.Locale.ROOT, "font/glyph_%02X.png", entry.getKey());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(entry.getValue(), "png", output)) throw new IOException("PNG encoding unavailable");
            bedrockAssets.put(name, output.toByteArray());
        }
        return count;
    }

    /**
     * Authored Java default-font bitmap providers (rank/tag icons in chat, tab, menus) become
     * Bedrock glyph cells too, so Bedrock shows the icon instead of a missing-glyph box.
     * The bitmap is split into the provider's chars grid like Java does. Codepoints in
     * Bedrock's reserved U+E000..U+E1FF pages, blank cells and cells already taken (managed
     * glyphs win) are skipped; migrate reserved literals with '/voxelcore admin pack glyphs'.
     */
    static int appendAuthored(Path contentRoot, Map<String, byte[]> javaAssets, Map<Integer, BufferedImage> sheets,
                              Map<String, byte[]> bedrockAssets) throws IOException {
        int count = 0;
        for (org.voxelhorizons.content.pack.ContentPack pack : new org.voxelhorizons.content.pack.ContentPackDiscovery().discover(contentRoot)) {
            Path font = pack.root().resolve("assets/minecraft/font/default.json");
            if (!java.nio.file.Files.isRegularFile(font)) continue;
            com.google.gson.JsonObject root;
            try {
                root = com.google.gson.JsonParser.parseString(new String(java.nio.file.Files.readAllBytes(font),
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (RuntimeException invalid) {
                continue; // The Java build reports malformed fonts; never block Bedrock on them.
            }
            if (!root.has("providers") || !root.get("providers").isJsonArray()) continue;
            for (com.google.gson.JsonElement element : root.getAsJsonArray("providers")) {
                if (!element.isJsonObject()) continue;
                com.google.gson.JsonObject provider = element.getAsJsonObject();
                if (!provider.has("type") || !"bitmap".equals(provider.get("type").getAsString())
                        || !provider.has("file") || !provider.has("chars")) continue;
                String file = provider.get("file").getAsString();
                String[] ref = file.contains(":") ? file.split(":", 2) : new String[]{"minecraft", file};
                String path = ref[1].endsWith(".png") ? ref[1] : ref[1] + ".png";
                byte[] bytes = javaAssets.get("assets/" + ref[0] + "/textures/" + path);
                if (bytes == null) continue;
                BufferedImage bitmap = ImageIO.read(new ByteArrayInputStream(bytes));
                if (bitmap == null) continue;
                java.util.List<int[]> rows = new java.util.ArrayList<int[]>();
                int columns = 0;
                for (com.google.gson.JsonElement row : provider.getAsJsonArray("chars")) {
                    int[] codePoints = row.getAsString().codePoints().toArray();
                    rows.add(codePoints);
                    columns = Math.max(columns, codePoints.length);
                }
                if (rows.isEmpty() || columns == 0) continue;
                int cellWidth = bitmap.getWidth() / columns, cellHeight = bitmap.getHeight() / rows.size();
                if (cellWidth < 1 || cellHeight < 1) continue;
                for (int r = 0; r < rows.size(); r++) {
                    int[] codePoints = rows.get(r);
                    for (int c = 0; c < codePoints.length; c++) {
                        int codePoint = codePoints[c];
                        if (codePoint == 0 || codePoint == ' ' || codePoint > 0xFFFF) continue;
                        if (codePoint >= 0xE000 && codePoint <= 0xE1FF) continue; // Bedrock controller glyphs.
                        if (codePoint < 0xE000) continue; // Ordinary text keeps Bedrock's own font.
                        BufferedImage icon = trim(bitmap.getSubimage(c * cellWidth, r * cellHeight, cellWidth, cellHeight));
                        // Fullscreen backgrounds would shrink to an unreadable speck in one text cell.
                        if (icon == null || icon.getWidth() > 64 || icon.getHeight() > 64) continue;
                        if (place(sheets, bedrockAssets, codePoint, icon)) count++;
                    }
                }
            }
        }
        return count;
    }

    /** Crop transparent margins; null when the cell is empty. */
    private static BufferedImage trim(BufferedImage cell) {
        int minX = cell.getWidth(), minY = cell.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < cell.getHeight(); y++) for (int x = 0; x < cell.getWidth(); x++) {
            if ((cell.getRGB(x, y) >>> 24) == 0) continue;
            minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
        }
        if (maxX < 0) return null;
        return cell.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /** Draw an icon into its Bedrock glyph cell; false when the cell is already occupied. */
    private static boolean place(Map<Integer, BufferedImage> sheets, Map<String, byte[]> bedrockAssets,
                                 int codePoint, BufferedImage icon) throws IOException {
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
        for (int yy=y; yy<y+cell; yy++) for(int xx=x;xx<x+cell;xx++)
            if ((sheet.getRGB(xx, yy) >>> 24) != 0) return false;
        double ratio = Math.min((double)cell/icon.getWidth(), (double)cell/icon.getHeight());
        int width = Math.max(1,(int)Math.round(icon.getWidth()*ratio));
        int height = Math.max(1,(int)Math.round(icon.getHeight()*ratio));
        Graphics2D graphics = sheet.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(icon,x+(cell-width)/2,y+(cell-height)/2,width,height,null);
        } finally { graphics.dispose(); }
        return true;
    }
}
