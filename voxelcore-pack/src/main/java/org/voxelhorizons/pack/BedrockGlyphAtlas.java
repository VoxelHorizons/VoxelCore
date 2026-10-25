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
 *
 * Bedrock draws a glyph-page pixel at a fixed on-screen size whatever the page resolution
 * (live tests: 512px pages doubled icons; wide rank tags squeezed into 16px cells were
 * unreadable), and trims each glyph's transparent columns. So every glyph is drawn at its
 * Java pixel size (font height) and each page's cell grows to fit its widest glyph, as the
 * Kafal converter does (e.g. 4096px pages, 256px cells for rank tags). Glyphs are centred.
 * Bedrock's vanilla glyph_E0 and glyph_E1 are never touched. This deliberately does
 * not translate Java GUI backgrounds, custom negative-spacing or text transforms.
 */
final class BedrockGlyphAtlas {
    private BedrockGlyphAtlas() {}

    /** Smallest generated cell (vanilla-like 256px page); larger glyphs grow their page. */
    static final int MIN_CELL = 16;
    /** Largest generated cell: 4096px pages, as the Kafal converter uses for rank tags. */
    static final int MAX_CELL = 256;

    static int append(Path contentRoot, Path allocationManifest, Map<String, byte[]> javaAssets,
                      Map<String, byte[]> bedrockAssets) throws IOException {
        return append(contentRoot, allocationManifest, javaAssets, bedrockAssets, new java.util.ArrayList<String>());
    }

    /** @param skipped receives one line per glyph that has no Bedrock cell, with the reason */
    static int append(Path contentRoot, Path allocationManifest, Map<String, byte[]> javaAssets,
                      Map<String, byte[]> bedrockAssets, java.util.List<String> skipped) throws IOException {
        UiGlyphRegistry glyphs = new UiGlyphLoader().load(contentRoot, allocationManifest, false);
        Map<Integer, BufferedImage> pending = new TreeMap<Integer, BufferedImage>();
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
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(source));
            BufferedImage icon = trim(original);
            if (icon == null) { skipped.add(label + ": empty or unreadable bitmap"); continue; }
            String unfit = unfit(icon);
            if (unfit != null) { skipped.add(label + ": " + unfit); continue; }
            // Java scales the whole bitmap to its font height; the trimmed part keeps its share.
            BufferedImage sized = atJavaSize(icon, visibleHeight(glyph.scaleRatio(), icon.getHeight(), original.getHeight()));
            if (pending.containsKey(Integer.valueOf(codePoint)) || occupiedByAuthoredPage(bedrockAssets, codePoint))
                throw new IOException("Bedrock font cell U+" + Integer.toHexString(codePoint).toUpperCase(java.util.Locale.ROOT)
                        + " is already occupied (managed glyph " + glyph.id() + ")");
            pending.put(Integer.valueOf(codePoint), sized);
            count++;
        }
        count += appendAuthored(contentRoot, javaAssets, pending, bedrockAssets, skipped);
        writePages(pending, bedrockAssets);
        return count;
    }

    /**
     * Authored Java default-font bitmap providers (rank/tag icons in chat, tab, menus) become
     * Bedrock glyph cells too, so Bedrock shows the icon instead of a missing-glyph box.
     * The bitmap is split into the provider's chars grid like Java does. Codepoints in
     * Bedrock's reserved U+E000..U+E1FF pages, blank cells and cells already taken (managed
     * glyphs win) are skipped; migrate reserved literals with '/voxelcore admin pack glyphs'.
     */
    static int appendAuthored(Path contentRoot, Map<String, byte[]> javaAssets, Map<Integer, BufferedImage> pending,
                              Map<String, byte[]> bedrockAssets, java.util.List<String> skipped) throws IOException {
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
                        if (pending.containsKey(Integer.valueOf(codePoint)) || occupiedByAuthoredPage(bedrockAssets, codePoint)) continue;
                        BufferedImage icon = trim(bitmap.getSubimage(c * cellWidth, r * cellHeight, cellWidth, cellHeight));
                        if (icon == null) continue;
                        String unfit = unfit(icon);
                        if (unfit != null) {
                            skipped.add("font glyph U+" + Integer.toHexString(codePoint).toUpperCase(java.util.Locale.ROOT)
                                    + " (" + file + "): " + unfit);
                            continue;
                        }
                        pending.put(Integer.valueOf(codePoint),
                                atJavaSize(icon, visibleHeight(javaHeight, icon.getHeight(), cellHeight)));
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** Why a bitmap cannot become an inline Bedrock glyph, or null when it can. */
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

    /** Java on-screen height of the trimmed part of a bitmap drawn at {@code javaHeight}. */
    static int visibleHeight(int javaHeight, int trimmedHeight, int fullHeight) {
        if (javaHeight <= 0 || fullHeight <= 0) return javaHeight;
        return Math.max(1, (int) Math.round((double) javaHeight * trimmedHeight / fullHeight));
    }

    /** The glyph as Java draws it: {@code javaHeight} pixels tall, proportional width (at most one max cell). */
    static BufferedImage atJavaSize(BufferedImage icon, int javaHeight) {
        double ratio = (double) Math.max(1, javaHeight <= 0 ? 8 : javaHeight) / icon.getHeight();
        ratio = Math.min(ratio, (double) MAX_CELL / Math.max(icon.getWidth(), icon.getHeight()));
        int width = Math.max(1, (int) Math.round(icon.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(icon.getHeight() * ratio));
        if (width == icon.getWidth() && height == icon.getHeight()) return icon;
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        draw(out, icon, 0, 0, width, height);
        return out;
    }

    /** Cell size for a generated page: fits its largest glyph, even, within MIN_CELL..MAX_CELL. */
    static int cellFor(Iterable<BufferedImage> glyphs) {
        int cell = MIN_CELL;
        for (BufferedImage glyph : glyphs) cell = Math.max(cell, Math.max(glyph.getWidth(), glyph.getHeight()));
        cell += cell & 1;
        return Math.min(MAX_CELL, cell);
    }

    private static boolean occupiedByAuthoredPage(Map<String, byte[]> bedrockAssets, int codePoint) throws IOException {
        byte[] authored = bedrockAssets.get(pageName(codePoint >>> 8));
        if (authored == null) return false;
        BufferedImage sheet = readAuthoredPage(authored, pageName(codePoint >>> 8));
        int cell = sheet.getWidth() / 16, slot = codePoint & 255, x = (slot % 16) * cell, y = (slot / 16) * cell;
        for (int yy = y; yy < y + cell; yy++) for (int xx = x; xx < x + cell; xx++)
            if ((sheet.getRGB(xx, yy) >>> 24) != 0) return true;
        return false;
    }

    private static BufferedImage readAuthoredPage(byte[] bytes, String name) throws IOException {
        BufferedImage sheet = ImageIO.read(new ByteArrayInputStream(bytes));
        if (sheet == null || sheet.getWidth() != sheet.getHeight() || sheet.getWidth() % 16 != 0 || sheet.getWidth() < 256)
            throw new IOException("Invalid authored Bedrock glyph atlas " + name);
        return sheet;
    }

    static String pageName(int page) {
        return String.format(java.util.Locale.ROOT, "font/glyph_%02X.png", page);
    }

    /** Draw every pending glyph centred in its page cell; authored pages keep their own cell size. */
    private static void writePages(Map<Integer, BufferedImage> pending, Map<String, byte[]> bedrockAssets) throws IOException {
        Map<Integer, Map<Integer, BufferedImage>> pages = new TreeMap<Integer, Map<Integer, BufferedImage>>();
        for (Map.Entry<Integer, BufferedImage> entry : pending.entrySet()) {
            int codePoint = entry.getKey().intValue();
            Map<Integer, BufferedImage> page = pages.get(Integer.valueOf(codePoint >>> 8));
            if (page == null) pages.put(Integer.valueOf(codePoint >>> 8), page = new TreeMap<Integer, BufferedImage>());
            page.put(Integer.valueOf(codePoint & 255), entry.getValue());
        }
        for (Map.Entry<Integer, Map<Integer, BufferedImage>> page : pages.entrySet()) {
            String name = pageName(page.getKey().intValue());
            byte[] authored = bedrockAssets.get(name);
            BufferedImage sheet;
            if (authored != null) sheet = readAuthoredPage(authored, name);
            else {
                int cell = cellFor(page.getValue().values());
                sheet = new BufferedImage(cell * 16, cell * 16, BufferedImage.TYPE_INT_ARGB);
            }
            int cell = sheet.getWidth() / 16;
            for (Map.Entry<Integer, BufferedImage> glyph : page.getValue().entrySet()) {
                int slot = glyph.getKey().intValue(), x = (slot % 16) * cell, y = (slot / 16) * cell;
                BufferedImage image = glyph.getValue();
                // An authored page with smaller cells than the glyph: fit (rare; generated pages always fit).
                double fit = Math.min(1.0, (double) cell / Math.max(image.getWidth(), image.getHeight()));
                int width = Math.max(1, (int) Math.round(image.getWidth() * fit));
                int height = Math.max(1, (int) Math.round(image.getHeight() * fit));
                draw(sheet, image, x + (cell - width) / 2, y + (cell - height) / 2, width, height);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(sheet, "png", output)) throw new IOException("PNG encoding unavailable");
            bedrockAssets.put(name, output.toByteArray());
        }
    }

    /** Downscale smoothly (high-res sources), upscale pixel-exact (pixel art). */
    private static void draw(BufferedImage sheet, BufferedImage image, int x, int y, int width, int height) {
        Graphics2D graphics = sheet.createGraphics();
        try {
            boolean shrinking = width < image.getWidth() || height < image.getHeight();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, shrinking
                    ? RenderingHints.VALUE_INTERPOLATION_BICUBIC : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(shrinking ? smoothShrink(image, width, height) : image, x, y, width, height, null);
        } finally { graphics.dispose(); }
    }

    /** Halve repeatedly before the final bicubic pass so large sources do not alias. */
    private static BufferedImage smoothShrink(BufferedImage image, int width, int height) {
        BufferedImage current = image;
        while (current.getWidth() / 2 >= width && current.getHeight() / 2 >= height) {
            BufferedImage half = new BufferedImage(Math.max(1, current.getWidth() / 2), Math.max(1, current.getHeight() / 2),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = half.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(current, 0, 0, half.getWidth(), half.getHeight(), null);
            } finally { graphics.dispose(); }
            current = half;
        }
        return current;
    }
}
