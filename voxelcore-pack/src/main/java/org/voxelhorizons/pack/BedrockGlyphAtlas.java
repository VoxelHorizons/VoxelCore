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
 * Bedrock's controller pages glyph_E0/E1 are only written when explicitly enabled. This deliberately does
 * not translate custom negative-spacing or text transforms; GUI backgrounds become chest-screen
 * images instead (BedrockChestGui).
 */
final class BedrockGlyphAtlas {
    private BedrockGlyphAtlas() {}

    /** Smallest generated cell (vanilla-like 256px page); larger glyphs grow their page. */
    static final int MIN_CELL = 16;
    /** Largest generated cell: 4096px pages, as the Kafal converter uses for rank tags. */
    static final int MAX_CELL = 256;
    /** Vanilla bitmap glyphs are 8px; twice that height counts as large inline artwork. */
    static final int LARGE_GLYPH_HEIGHT = 16;
    /** Java heights above this are oversized artwork (logos, banners) that must be reduced to fit a page cell. */
    static final int INLINE_MAX_HEIGHT = 32;
    /** Default longest side for an oversized glyph without an explicit Bedrock size. */
    static final int DEFAULT_OVERSIZED_MAX_SIZE = 128;

    static int append(Path contentRoot, Path allocationManifest, Map<String, byte[]> javaAssets,
                      Map<String, byte[]> bedrockAssets) throws IOException {
        return append(contentRoot, allocationManifest, javaAssets, bedrockAssets, new java.util.ArrayList<String>());
    }

    /** @param skipped receives one line per glyph that has no Bedrock cell, with the reason */
    static int append(Path contentRoot, Path allocationManifest, Map<String, byte[]> javaAssets,
                      Map<String, byte[]> bedrockAssets, java.util.List<String> skipped) throws IOException {
        return append(contentRoot, allocationManifest, javaAssets, bedrockAssets, skipped, false, Shrink.AREA);
    }

    static int append(Path contentRoot, Path allocationManifest, Map<String, byte[]> javaAssets,
                      Map<String, byte[]> bedrockAssets, java.util.List<String> skipped,
                      boolean overrideControllerPages, Shrink shrink) throws IOException {
        return append(contentRoot, allocationManifest, javaAssets, bedrockAssets, skipped,
                overrideControllerPages, shrink, DEFAULT_OVERSIZED_MAX_SIZE);
    }

    /**
     * How a glyph taller than its Bedrock target is reduced. Bedrock draws a glyph-page pixel at
     * a fixed size, so a 16px flag shown 10px tall cannot keep every pixel: AREA keeps all detail
     * softly, NEAREST stays crisp but drops rows and columns, NATIVE never shrinks (crisp, but
     * larger than on Java, as the Kafal converter does).
     */
    enum Shrink { AREA, NEAREST, NATIVE }

    /**
     * @param oversizedMaxSize longest side, in page pixels, an oversized glyph (Java height above
     *        {@link #INLINE_MAX_HEIGHT}) is reduced to; the page cell grows to match, so a lower value
     *        keeps the glyph page smaller. A glyph with {@code bedrock_height} or {@code bedrock_scale}
     *        may use up to {@link #MAX_CELL} regardless.
     * @param overrideControllerPages also write authored glyphs at U+E000..U+E1FF (Bedrock's
     *        controller-icon pages glyph_E0/E1), as the Kafal converter does: the glyphs show,
     *        but Bedrock's own button icons on a written page are replaced for every player
     */
    static int append(Path contentRoot, Path allocationManifest, Map<String, byte[]> javaAssets,
                      Map<String, byte[]> bedrockAssets, java.util.List<String> skipped,
                      boolean overrideControllerPages, Shrink shrink, int oversizedMaxSize) throws IOException {
        int oversizedLimit = Math.max(MIN_CELL, Math.min(MAX_CELL, oversizedMaxSize));
        UiGlyphRegistry glyphs = new UiGlyphLoader().load(contentRoot, allocationManifest, false);
        Map<Integer, Glyph> pending = new TreeMap<Integer, Glyph>();
        int count = 0;
        for (UiGlyphDefinition glyph : glyphs.entries().values()) {
            String label = "glyph " + glyph.id();
            // GUI overlays (menu backgrounds) and tall pictures are Java screen art, not inline text.
            if (glyph.gui()) continue;
            boolean oversized = glyph.scaleRatio() > INLINE_MAX_HEIGHT;
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
            // Use the untrimmed source height to distinguish native-resolution large
            // glyphs from font bitmaps that Java explicitly downscales. Cropping must not
            // affect this decision.
            Glyph sized;
            if (oversized || glyph.bedrockHeight() > 0 || glyph.bedrockScale() > 0) {
                int full = oversizedHeight(glyph.scaleRatio(), glyph.bedrockHeight(), glyph.bedrockScale());
                boolean explicit = glyph.bedrockHeight() > 0 || glyph.bedrockScale() > 0;
                sized = Glyph.oversized(reduceBlocks(icon),
                        visibleHeight(full, icon.getHeight(), original.getHeight()),
                        explicit ? MAX_CELL : oversizedLimit);
            } else {
                sized = new Glyph(reduceBlocks(icon),
                        visibleHeight(glyph.scaleRatio(), icon.getHeight(), original.getHeight()),
                        (double) original.getHeight() / glyph.scaleRatio(), glyph.scaleRatio(),
                        javaVerticalOffset(glyph.scaleRatio(), glyph.ascent(), original.getHeight(),
                                transparentTopRows(original), icon.getHeight()));
            }
            if (pending.containsKey(Integer.valueOf(codePoint)) || occupiedByAuthoredPage(bedrockAssets, codePoint))
                throw new IOException("Bedrock font cell U+" + Integer.toHexString(codePoint).toUpperCase(java.util.Locale.ROOT)
                        + " is already occupied (managed glyph " + glyph.id() + ")");
            pending.put(Integer.valueOf(codePoint), sized);
            count++;
        }
        count += appendAuthored(contentRoot, javaAssets, pending, bedrockAssets, skipped, overrideControllerPages, oversizedLimit);
        double ratio = pixelRatio(pending.values());
        Map<Integer, BufferedImage> drawn = new TreeMap<Integer, BufferedImage>();
        Map<Integer, Integer> verticalOffsets = new TreeMap<Integer, Integer>();
        for (Map.Entry<Integer, Glyph> entry : pending.entrySet()) {
            Glyph glyph = entry.getValue();
            // The oversized path is deliberately independent of the normal-font ascent calibration.
            double glyphRatio = effectiveRatio(glyph, ratio);
            drawn.put(entry.getKey(), glyph.maxSide > 0
                    ? sizedOversized(glyph.art, glyph.javaHeight, glyph.maxSide, shrink)
                    : sized(glyph.art, glyph.javaHeight, glyphRatio, shrink));
            verticalOffsets.put(entry.getKey(), Integer.valueOf(glyph.maxSide > 0 ? 0
                    : (int) Math.round(glyph.javaVerticalOffset * glyphRatio)));
        }
        writePages(drawn, verticalOffsets, bedrockAssets);
        return count;
    }

    /**
     * Authored Java default-font bitmap providers (rank/tag icons in chat, tab, menus) become
     * Bedrock glyph cells too, so Bedrock shows the icon instead of a missing-glyph box.
     * The bitmap is split into the provider's chars grid like Java does. Codepoints in
     * Bedrock's reserved U+E000..U+E1FF pages, blank cells and cells already taken (managed
     * glyphs win) are skipped; migrate reserved literals with '/voxelcore admin pack glyphs'.
     */
    static int appendAuthored(Path contentRoot, Map<String, byte[]> javaAssets, Map<Integer, Glyph> pending,
                              Map<String, byte[]> bedrockAssets, java.util.List<String> skipped,
                              boolean overrideControllerPages) throws IOException {
        return appendAuthored(contentRoot, javaAssets, pending, bedrockAssets, skipped,
                overrideControllerPages, DEFAULT_OVERSIZED_MAX_SIZE);
    }

    /**
     * Also reads custom font files ({@code assets/<namespace>/font/*.json}). Bedrock has one
     * font, so their private-use characters are written to the same glyph pages. Providers
     * taller than {@link #INLINE_MAX_HEIGHT} in a custom font are logos and are reduced to
     * {@code oversizedLimit}; providers with a negative ascent are drawn below the text line
     * (screen overlays) and are skipped.
     */
    static int appendAuthored(Path contentRoot, Map<String, byte[]> javaAssets, Map<Integer, Glyph> pending,
                              Map<String, byte[]> bedrockAssets, java.util.List<String> skipped,
                              boolean overrideControllerPages, int oversizedLimit) throws IOException {
        int count = 0;
        for (org.voxelhorizons.content.pack.ContentPack pack : new org.voxelhorizons.content.pack.ContentPackDiscovery().discover(contentRoot)) {
            java.util.List<Path> fonts = new java.util.ArrayList<Path>();
            Path assets = pack.root().resolve("assets");
            if (java.nio.file.Files.isDirectory(assets)) {
                try (java.util.stream.Stream<Path> walk = java.nio.file.Files.walk(assets, 3)) {
                    walk.filter(f -> f.getParent() != null && f.getParent().getFileName() != null
                            && "font".equals(f.getParent().getFileName().toString())
                            && f.getFileName().toString().endsWith(".json")
                            && java.nio.file.Files.isRegularFile(f)).forEach(fonts::add);
                }
            }
            java.util.Collections.sort(fonts);
            Path defaultFont = assets.resolve("minecraft/font/default.json");
            for (Path font : fonts) {
                boolean custom = !font.equals(defaultFont);
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
                    boolean oversized = javaHeight > INLINE_MAX_HEIGHT;
                    if (oversized && !custom) continue;
                    if (custom && provider.has("ascent") && provider.get("ascent").getAsInt() < 0) continue;
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
                    int javaAscent = provider.has("ascent") ? provider.get("ascent").getAsInt() : Math.min(7, javaHeight);
                    for (int r = 0; r < rows.size(); r++) {
                        int[] codePoints = rows.get(r);
                        for (int c = 0; c < codePoints.length; c++) {
                            int codePoint = codePoints[c];
                            if (codePoint == 0 || codePoint == ' ' || codePoint > 0xFFFF) continue;
                            if (codePoint >= 0xE000 && codePoint <= 0xE1FF && !overrideControllerPages) {
                                skipped.add("font glyph U+" + Integer.toHexString(codePoint).toUpperCase(java.util.Locale.ROOT)
                                        + " (" + file + "): in Bedrock's controller-icon pages; enable "
                                        + "bedrock.override_controller_glyph_pages or migrate it with /voxelcore admin pack glyphs");
                                continue;
                            }
                            if (codePoint < 0xE000) continue; // Ordinary text keeps Bedrock's own font.
                            if (pending.containsKey(Integer.valueOf(codePoint)) || occupiedByAuthoredPage(bedrockAssets, codePoint)) continue;
                            BufferedImage sourceCell = bitmap.getSubimage(c * cellWidth, r * cellHeight, cellWidth, cellHeight);
                            BufferedImage icon = trim(sourceCell);
                            if (icon == null) continue;
                            String unfit = unfit(icon);
                            if (unfit != null) {
                                skipped.add("font glyph U+" + Integer.toHexString(codePoint).toUpperCase(java.util.Locale.ROOT)
                                        + " (" + file + "): " + unfit);
                                continue;
                            }
                            pending.put(Integer.valueOf(codePoint), oversized
                                    ? Glyph.oversized(reduceBlocks(icon),
                                            visibleHeight(javaHeight, icon.getHeight(), cellHeight), oversizedLimit)
                                    : new Glyph(reduceBlocks(icon), visibleHeight(javaHeight, icon.getHeight(), cellHeight),
                                            (double) cellHeight / javaHeight, javaHeight,
                                            javaVerticalOffset(javaHeight, javaAscent, cellHeight,
                                                    transparentTopRows(sourceCell), icon.getHeight())));
                            count++;
                        }
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

    /** Number of transparent rows before the first visible pixel in the original bitmap cell. */
    private static int transparentTopRows(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) return y;
            }
        }
        return image.getHeight();
    }

    /**
     * Vertical displacement of the visible bitmap's centre from VoxelCore's normal
     * 8px UI bitmap glyph (default ascent 8). Unlike the cropped texture, this retains the provider's ascent
     * and the transparent top margin in the original cell. Positive moves art down.
     * Convert this Java-pixel displacement to Bedrock atlas pixels using the same ratio
     * used to size that individual glyph.
     */
    static double javaVerticalOffset(int height, int ascent, int sourceHeight, int transparentTop, int visibleSourceHeight) {
        if (height <= 0 || sourceHeight <= 0) return 0.0;
        double visibleCentre = (transparentTop + visibleSourceHeight / 2.0) * height / sourceHeight;
        return (7.0 - ascent) + visibleCentre - 3.0;
    }

    /** Java on-screen height of the trimmed part of a bitmap drawn at {@code javaHeight}. */
    static int visibleHeight(int javaHeight, int trimmedHeight, int fullHeight) {
        if (javaHeight <= 0 || fullHeight <= 0) return javaHeight;
        return Math.max(1, (int) Math.round((double) javaHeight * trimmedHeight / fullHeight));
    }

    /** Java's actual bitmap height and the source art's untrimmed pixel density. */
    static final class Glyph {
        final BufferedImage art;
        final int javaHeight;
        final double sourcePixelsPerJavaPixel;
        final int providerHeight;
        /** Longest allowed side for an oversized glyph baked at exactly javaHeight; 0 for ordinary glyphs. */
        final int maxSide;
        final double javaVerticalOffset;
        static Glyph oversized(BufferedImage art, int bakedHeight, int maxSide) {
            return new Glyph(art, bakedHeight, Double.POSITIVE_INFINITY, bakedHeight, maxSide, 0.0);
        }
        Glyph(BufferedImage art, int javaHeight) {
            this(art, javaHeight, Double.POSITIVE_INFINITY, javaHeight);
        }
        Glyph(BufferedImage art, int javaHeight, double sourcePixelsPerJavaPixel, int providerHeight) {
            this(art, javaHeight, sourcePixelsPerJavaPixel, providerHeight, 0, 0.0);
        }
        Glyph(BufferedImage art, int javaHeight, double sourcePixelsPerJavaPixel,
              int providerHeight, double javaVerticalOffset) {
            this(art, javaHeight, sourcePixelsPerJavaPixel, providerHeight, 0, javaVerticalOffset);
        }
        private Glyph(BufferedImage art, int javaHeight, double sourcePixelsPerJavaPixel,
                      int providerHeight, int maxSide, double javaVerticalOffset) {
            this.maxSide = maxSide;
            this.art = art;
            this.javaHeight = Math.max(1, javaHeight <= 0 ? 8 : javaHeight);
            this.sourcePixelsPerJavaPixel = sourcePixelsPerJavaPixel;
            this.providerHeight = providerHeight;
            this.javaVerticalOffset = javaVerticalOffset;
        }
    }

    /**
     * Rank tags and other normal-height font glyphs need the shared Bedrock correction.
     * A larger bitmap already authored at its native Java height must not get an extra
     * enlargement on top: Bedrock text displays otherwise show it oversized. Limit that
     * *additional* upscale by the source's actual pixels per Java font pixel; never
     * downscale beyond what the Java font definition requests, or change smaller icons.
     */
    static double effectiveRatio(Glyph glyph, double sharedRatio) {
        if (glyph.providerHeight < LARGE_GLYPH_HEIGHT) return sharedRatio;
        return Math.min(sharedRatio, Math.max(1.0, glyph.sourcePixelsPerJavaPixel));
    }

    /**
     * Bedrock page pixels per Java pixel, calculated from Java bitmap dimensions.
     * Shared by normal-height glyphs; native-resolution large glyphs cap only their
     * own extra enlargement so unrelated ranks and HUDs remain unchanged.
     * Java (live test: a 16px flag next to 11px rank tags of the same Java height looked too big).
     * Taken from the wide text tags (aspect >= 2), whose lettering suffers most from resampling,
     * so they stay pixel-exact; otherwise from all pixel-art glyphs. Median, clamped to 0.25..4.
     */
    static double pixelRatio(java.util.Collection<Glyph> glyphs) {
        java.util.List<Double> tags = new java.util.ArrayList<Double>(), all = new java.util.ArrayList<Double>();
        for (Glyph glyph : glyphs) {
            if (glyph.maxSide > 0) continue; // Oversized art is fitted separately and says nothing about tag scale.
            double ratio = (double) glyph.art.getHeight() / glyph.javaHeight;
            if (ratio > 4) continue; // Large images are resampled anyway.
            if (glyph.art.getHeight() < 4) continue; // Flat colour reduces to ~1px: says nothing about scale.
            all.add(ratio);
            if (glyph.art.getWidth() >= glyph.art.getHeight() * 2) tags.add(ratio);
        }
        java.util.List<Double> source = tags.isEmpty() ? all : tags;
        if (source.isEmpty()) return 1.0;
        java.util.Collections.sort(source);
        // Below 1 is normal: Java often draws small pixel art larger than native (live test: 11px
        // tags at Java height ~18 with 16px flags at ~16); a floor of 1 made the flags too big.
        return Math.max(0.25, Math.min(4.0, source.get(source.size() / 2)));
    }

    /**
     * The glyph at {@code javaHeight * ratio} page pixels: unchanged when it already is, whole
     * multiples (else nearest) when growing, nearest (pixel art stays crisp) when shrinking by
     * less than half, and smooth only for genuinely large images.
     */
    static BufferedImage sized(BufferedImage art, int javaHeight, double ratio) {
        return sized(art, javaHeight, ratio, Shrink.AREA);
    }

    static BufferedImage sized(BufferedImage art, int javaHeight, double ratio, Shrink shrink) {
        int target = Math.max(1, (int) Math.round(javaHeight * ratio));
        int height = art.getHeight();
        BufferedImage out;
        if (height == target || (height > target && height <= target * 2 && shrink == Shrink.NATIVE)) out = art;
        else {
            int width = Math.max(1, (int) Math.round((double) art.getWidth() * target / height));
            if (height < target && target % height == 0) width = art.getWidth() * (target / height);
            // Growing: nearest keeps pixel art crisp. Shrinking: area averaging (premultiplied
            // alpha) keeps thin details such as flag stars that nearest would drop (live test).
            out = height < target || shrink == Shrink.NEAREST ? nearest(art, width, target) : areaAverage(art, width, target);
        }
        if (Math.max(out.getWidth(), out.getHeight()) > MAX_CELL) {
            double fit = (double) MAX_CELL / Math.max(out.getWidth(), out.getHeight());
            out = scaled(out, Math.max(1, (int) (out.getWidth() * fit)), Math.max(1, (int) (out.getHeight() * fit)));
        }
        return out;
    }

    /**
     * Full baked height of an oversized glyph in page pixels: an explicit {@code bedrockHeight}
     * wins, then {@code javaHeight * bedrockScale}, else the Java height. Always within 1..MAX_CELL.
     */
    static int oversizedHeight(int javaHeight, int bedrockHeight, double bedrockScale) {
        double height = bedrockHeight > 0 ? bedrockHeight
                : bedrockScale > 0 ? Math.round(javaHeight * bedrockScale) : javaHeight;
        return (int) Math.max(1, Math.min(MAX_CELL, height));
    }

    /**
     * Fits large artwork to {@code targetHeight} page pixels tall with its aspect ratio kept,
     * then shrinks both sides further when the longest exceeds {@code maxSide}. Reduction uses
     * area averaging (or nearest with {@link Shrink#NEAREST}); enlarging uses nearest.
     */
    static BufferedImage sizedOversized(BufferedImage art, int targetHeight, int maxSide, Shrink shrink) {
        int limit = Math.max(1, Math.min(MAX_CELL, maxSide));
        double aspect = (double) art.getWidth() / art.getHeight();
        double height = Math.max(1, targetHeight);
        double width = height * aspect;
        double longest = Math.max(width, height);
        if (longest > limit) {
            double fit = limit / longest;
            width *= fit;
            height *= fit;
        }
        int w = Math.max(1, (int) Math.round(width)), h = Math.max(1, (int) Math.round(height));
        w = Math.min(w, limit);
        h = Math.min(h, limit);
        if (w == art.getWidth() && h == art.getHeight()) return art;
        boolean shrinking = w < art.getWidth() || h < art.getHeight();
        return shrinking && shrink != Shrink.NEAREST ? areaAverage(art, w, h) : nearest(art, w, h);
    }

    /** Box-filter downscale weighting each source pixel by its covered area, in premultiplied alpha. */
    static BufferedImage areaAverage(BufferedImage image, int width, int height) {
        int sw = image.getWidth(), sh = image.getHeight();
        double fx = (double) sw / width, fy = (double) sh / height;
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            double x0 = x * fx, x1 = x0 + fx, y0 = y * fy, y1 = y0 + fy, a = 0, r = 0, g = 0, b = 0, area = 0;
            for (int sy = (int) y0; sy < Math.min(sh, Math.ceil(y1)); sy++) {
                double wy = Math.min(y1, sy + 1) - Math.max(y0, sy);
                for (int sx = (int) x0; sx < Math.min(sw, Math.ceil(x1)); sx++) {
                    double w = wy * (Math.min(x1, sx + 1) - Math.max(x0, sx));
                    if (w <= 0) continue;
                    int argb = image.getRGB(sx, sy);
                    double alpha = (argb >>> 24) / 255.0;
                    a += alpha * w;
                    r += ((argb >> 16) & 255) * alpha * w;
                    g += ((argb >> 8) & 255) * alpha * w;
                    b += (argb & 255) * alpha * w;
                    area += w;
                }
            }
            if (a <= 0) continue;
            int alpha = (int) Math.round(255 * a / area);
            out.setRGB(x, y, (alpha << 24) | ((int) Math.round(r / a) << 16) | ((int) Math.round(g / a) << 8) | (int) Math.round(b / a));
        }
        return out;
    }

    private static BufferedImage nearest(BufferedImage image, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            out.setRGB(x, y, image.getRGB(Math.min(image.getWidth() - 1, x * image.getWidth() / width),
                    Math.min(image.getHeight() - 1, y * image.getHeight() / height)));
        return out;
    }

    /** Undo an integer upscale: the largest b where the image is made of uniform b x b blocks. */
    static BufferedImage reduceBlocks(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        for (int b = Math.min(w, h); b > 1; b--) {
            if (w % b != 0 || h % b != 0 || !uniformBlocks(image, b)) continue;
            BufferedImage out = new BufferedImage(w / b, h / b, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h / b; y++) for (int x = 0; x < w / b; x++) out.setRGB(x, y, image.getRGB(x * b, y * b));
            return out;
        }
        return image;
    }

    private static boolean uniformBlocks(BufferedImage image, int b) {
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            if (image.getRGB(x, y) != image.getRGB(x - x % b, y - y % b)) return false;
        return true;
    }

    private static BufferedImage scaled(BufferedImage image, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        draw(out, image, 0, 0, width, height);
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

    /**
     * Keep the existing centred page-cell layout as the reference used by rank and HUD
     * glyphs, but shift each bitmap by its own Java ascent/transparent-padding metrics.
     * Authored pages keep their existing cell size. Out-of-cell positions are clamped:
     * a Bedrock glyph sheet cannot represent pixels outside a fixed-size cell.
     */
    private static void writePages(Map<Integer, BufferedImage> pending, Map<Integer, Integer> verticalOffsets,
                                   Map<String, byte[]> bedrockAssets) throws IOException {
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
                int centeredY = (cell - height) / 2;
                int codePoint = (page.getKey().intValue() << 8) | glyph.getKey().intValue();
                int offsetY = verticalOffsets.getOrDefault(Integer.valueOf(codePoint), Integer.valueOf(0)).intValue();
                int glyphY = Math.max(0, Math.min(cell - height, centeredY + offsetY));
                draw(sheet, image, x + (cell - width) / 2, y + glyphY, width, height);
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
