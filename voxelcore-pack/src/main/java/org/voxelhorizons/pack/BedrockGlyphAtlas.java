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

    /** New Bedrock glyph pages: 512px = 32px cells, so high-res Java icons stay crisp. */
    static final int SHEET_SIZE = 512;
    /**
     * Bedrock draws every glyph as one square cell, so a wide rank tag squeezed into one cell
     * becomes unreadable. Glyphs wider than this ratio are also cut into square tiles at
     * Bedrock-only codepoints (U+F400..U+F8FF); VoxelCore swaps the Java character for the
     * tile sequence in text sent to Bedrock players (see glyph-tiles.json). The original
     * codepoint keeps a whole, scaled-down copy for text VoxelCore cannot rewrite.
     */
    static final double TILE_ASPECT = 1.25;
    static final int FIRST_TILE = 0xF400, LAST_TILE = 0xF8FF;

    static int append(Path contentRoot, Path allocationManifest, Map<String, byte[]> javaAssets,
                      Map<String, byte[]> bedrockAssets) throws IOException {
        return append(contentRoot, allocationManifest, javaAssets, bedrockAssets, new java.util.ArrayList<String>(),
                new TreeMap<Integer, int[]>());
    }

    /**
     * @param skipped receives one line per glyph that has no Bedrock cell, with the reason
     * @param tiles   receives Java codepoint -> Bedrock tile codepoints for wide glyphs
     */
    static int append(Path contentRoot, Path allocationManifest, Map<String, byte[]> javaAssets,
                      Map<String, byte[]> bedrockAssets, java.util.List<String> skipped,
                      Map<Integer, int[]> tiles) throws IOException {
        UiGlyphRegistry glyphs = new UiGlyphLoader().load(contentRoot, allocationManifest, false);
        Map<Integer, BufferedImage> sheets = new TreeMap<Integer, BufferedImage>();
        int count = 0;
        for (UiGlyphDefinition glyph : glyphs.entries().values()) {
            String label = "glyph " + glyph.id();
            // GUI overlays (menu backgrounds) and tall pictures are Java screen art, not inline text.
            if (glyph.gui()) continue;
            if (glyph.scaleRatio() > 32) { skipped.add(label + ": Java height " + glyph.scaleRatio() + " is too tall for a Bedrock text glyph"); continue; }
            int codePoint = glyph.codePoint();
            if (codePoint < 0xE200 || codePoint > 0xF3FF) { skipped.add(label + ": codepoint outside U+E200..U+F3FF"); continue; }
            String[] ref = glyph.texture().split(":", 2);
            byte[] source = javaAssets.get("assets/" + ref[0] + "/textures/" + ref[1] + ".png");
            // A broken glyph bitmap is the Java build's error to report; never block Bedrock on it.
            if (source == null) { skipped.add(label + ": texture " + glyph.texture() + " not found"); continue; }
            BufferedImage icon = trim(ImageIO.read(new ByteArrayInputStream(source)));
            if (icon == null) { skipped.add(label + ": empty or unreadable bitmap"); continue; }
            String unfit = unfit(icon);
            if (unfit != null) { skipped.add(label + ": " + unfit); continue; }
            if (!place(sheets, bedrockAssets, codePoint, icon))
                throw new IOException("Bedrock font cell U+" + Integer.toHexString(codePoint).toUpperCase(java.util.Locale.ROOT)
                        + " is already occupied (managed glyph " + glyph.id() + ")");
            tile(sheets, bedrockAssets, codePoint, icon, tiles, skipped, label);
            count++;
        }
        count += appendAuthored(contentRoot, javaAssets, sheets, bedrockAssets, skipped, tiles);
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
                              Map<String, byte[]> bedrockAssets, java.util.List<String> skipped,
                              Map<Integer, int[]> tiles) throws IOException {
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
                int javaHeight = provider.has("height") ? provider.get("height").getAsInt() : 8;
                // Java screen art (menu backgrounds) is drawn tall; it is not inline text.
                if (javaHeight > 32) continue;
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
                        if (icon == null) continue;
                        String unfit = unfit(icon);
                        if (unfit != null) {
                            skipped.add("font glyph U+" + Integer.toHexString(codePoint).toUpperCase(java.util.Locale.ROOT)
                                    + " (" + file + "): " + unfit);
                            continue;
                        }
                        if (place(sheets, bedrockAssets, codePoint, icon)) {
                            tile(sheets, bedrockAssets, codePoint, icon, tiles, skipped,
                                    "font glyph U+" + Integer.toHexString(codePoint).toUpperCase(java.util.Locale.ROOT));
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    /**
     * Why a bitmap cannot become one square Bedrock text glyph, or null when it can. Large
     * square icons are fine (scaled into the cell); very wide tags and fullscreen art are not.
     */
    static String unfit(BufferedImage icon) {
        if (icon.getWidth() > 1024 || icon.getHeight() > 1024)
            return icon.getWidth() + "x" + icon.getHeight() + " px is fullscreen art, not an inline glyph";
        return null;
    }

    /** Crop transparent margins; null when the cell is empty or unreadable. */
    private static BufferedImage trim(BufferedImage cell) {
        if (cell == null) return null;
        int minX = cell.getWidth(), minY = cell.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < cell.getHeight(); y++) for (int x = 0; x < cell.getWidth(); x++) {
            if ((cell.getRGB(x, y) >>> 24) == 0) continue;
            minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
        }
        if (maxX < 0) return null;
        return cell.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /**
     * Cut a wide glyph into square tiles (side = its height) at the next free Bedrock-only
     * codepoints. Tiles are drawn at an integer scale where possible so pixel art stays sharp.
     */
    private static void tile(Map<Integer, BufferedImage> sheets, Map<String, byte[]> bedrockAssets, int codePoint,
                             BufferedImage icon, Map<Integer, int[]> tiles, java.util.List<String> skipped,
                             String label) throws IOException {
        if ((double) icon.getWidth() / icon.getHeight() <= TILE_ASPECT) return;
        int side = icon.getHeight(), count = (icon.getWidth() + side - 1) / side;
        int next = FIRST_TILE;
        for (int[] used : tiles.values()) for (int tile : used) next = Math.max(next, tile + 1);
        if (next + count - 1 > LAST_TILE) {
            skipped.add(label + ": no Bedrock tile codepoints left; shown squeezed into one cell");
            return;
        }
        int[] sequence = new int[count];
        for (int i = 0; i < count; i++) {
            int width = Math.min(side, icon.getWidth() - i * side);
            BufferedImage part = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = part.createGraphics();
            try { graphics.drawImage(icon.getSubimage(i * side, 0, width, side), 0, 0, null); }
            finally { graphics.dispose(); }
            if (!placeTile(sheets, bedrockAssets, next + i, part, width))
                throw new IOException("Bedrock tile cell U+" + Integer.toHexString(next + i) + " is already occupied");
            sequence[i] = next + i;
        }
        tiles.put(Integer.valueOf(codePoint), sequence);
    }

    /** Like {@link #place} for a tile: left/top aligned (no centring) so neighbours line up. */
    private static boolean placeTile(Map<Integer, BufferedImage> sheets, Map<String, byte[]> bedrockAssets,
                                     int codePoint, BufferedImage tile, int contentWidth) throws IOException {
        BufferedImage sheet = sheet(sheets, bedrockAssets, codePoint >>> 8);
        int cell = sheet.getWidth() / 16;
        int slot = codePoint & 255, x = (slot % 16) * cell, y = (slot / 16) * cell;
        for (int yy=y; yy<y+cell; yy++) for(int xx=x;xx<x+cell;xx++)
            if ((sheet.getRGB(xx, yy) >>> 24) != 0) return false;
        int side = tile.getHeight();
        double scale = side <= cell ? Math.floor((double) cell / side) : (double) cell / side;
        int drawn = (int) Math.round(side * scale);
        Graphics2D graphics = sheet.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(tile, x, y + (cell - drawn) / 2, drawn, drawn, null);
        } finally { graphics.dispose(); }
        return true;
    }

    private static BufferedImage sheet(Map<Integer, BufferedImage> sheets, Map<String, byte[]> bedrockAssets,
                                       int page) throws IOException {
        String name = String.format(java.util.Locale.ROOT, "font/glyph_%02X.png", page);
        BufferedImage sheet = sheets.get(page);
        if (sheet == null) {
            byte[] authored = bedrockAssets.get(name);
            if (authored != null) {
                sheet = ImageIO.read(new ByteArrayInputStream(authored));
                if (sheet == null || sheet.getWidth() != sheet.getHeight()
                        || sheet.getWidth() % 16 != 0 || sheet.getWidth() < 256)
                    throw new IOException("Invalid authored Bedrock glyph atlas " + name);
            } else sheet = new BufferedImage(SHEET_SIZE, SHEET_SIZE, BufferedImage.TYPE_INT_ARGB);
            sheets.put(page, sheet);
        }
        return sheet;
    }

    /** Draw an icon into its Bedrock glyph cell; false when the cell is already occupied. */
    private static boolean place(Map<Integer, BufferedImage> sheets, Map<String, byte[]> bedrockAssets,
                                 int codePoint, BufferedImage icon) throws IOException {
        BufferedImage sheet = sheet(sheets, bedrockAssets, codePoint >>> 8);
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
