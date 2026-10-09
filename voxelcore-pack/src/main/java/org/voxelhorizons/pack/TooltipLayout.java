package org.voxelhorizons.pack;

import org.voxelhorizons.content.ContentID;

/**
 * Composes a popup using ordinary, content-defined UI glyphs. Each glyph advance
 * includes Minecraft's trailing one-pixel spacing; overlap removes that seam.
 */
public final class TooltipLayout {
    private final UiGlyphDefinition left;
    private final UiGlyphDefinition center;
    private final UiGlyphDefinition right;
    private final int overlap;
    private final int padding;
    private final int xOffset;
    private final int letterSpacing;
    private final int wordSpacing;

    public TooltipLayout(UiGlyphRegistry registry, String leftId, String centerId, String rightId,
                         int overlap, int padding, int xOffset) {
        this(registry, leftId, centerId, rightId, overlap, padding, xOffset, 0, 0);
    }

    public TooltipLayout(UiGlyphRegistry registry, String leftId, String centerId, String rightId,
                         int overlap, int padding, int xOffset, int letterSpacing, int wordSpacing) {
        if (registry == null) throw new IllegalArgumentException("Missing UI glyph registry");
        this.left = required(registry, leftId);
        this.center = required(registry, centerId);
        this.right = required(registry, rightId);
        if (overlap < 0 || overlap >= Math.min(left.advance(), Math.min(center.advance(), right.advance()))) {
            throw new IllegalArgumentException("Tooltip tile_overlap must be nonnegative and smaller than every glyph advance");
        }
        if (padding < 0 || padding > 256) throw new IllegalArgumentException("Tooltip horizontal_padding must be 0..256");
        if (xOffset < -1024 || xOffset > 1024) throw new IllegalArgumentException("Tooltip x_offset must be -1024..1024");
        this.overlap = overlap;
        this.padding = padding;
        this.xOffset = xOffset;
        if (letterSpacing < -2 || letterSpacing > 4 || wordSpacing < -3 || wordSpacing > 8) {
            throw new IllegalArgumentException("Tooltip letter_spacing must be -2..4 and word_spacing -3..8");
        }
        this.letterSpacing = letterSpacing;
        this.wordSpacing = wordSpacing;
    }

    private static UiGlyphDefinition required(UiGlyphRegistry registry, String id) {
        final ContentID parsed;
        try { parsed = ContentID.parse(id, "voxel"); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Invalid tooltip UI glyph ID: " + id, exception); }
        UiGlyphDefinition definition = registry.get(parsed).orElse(null);
        if (definition == null) throw new IllegalArgumentException("Missing tooltip UI glyph '" + parsed
                + "'. Define it under ui: in a content pack and rebuild the resource pack.");
        return definition;
    }

    public String compose(String line1, String line2, String line3) {
        String[] lines = {line1 == null ? "" : line1, line2 == null ? "" : line2, line3 == null ? "" : line3};
        int maxWidth = Math.max(TooltipGlyphs.width(lines[0], letterSpacing, wordSpacing),
                Math.max(TooltipGlyphs.width(lines[1], letterSpacing, wordSpacing), TooltipGlyphs.width(lines[2], letterSpacing, wordSpacing)));
        int requestedWidth = maxWidth + 2 * padding;
        int boxWidth = left.advance() + right.advance() - overlap;
        int step = center.advance() - overlap;
        int count = Math.max(0, (requestedWidth - boxWidth + step - 1) / step);
        boxWidth += count * step;


        StringBuilder output = new StringBuilder();
        output.append(UiSpacingGlyphs.charactersForOffset(xOffset)).append("\u00A7f");
        output.append(left.character());
        for (int i = 0; i < count; i++) {
            output.append(UiSpacingGlyphs.charactersForOffset(-overlap)).append(center.character());
        }
        output.append(UiSpacingGlyphs.charactersForOffset(-overlap)).append(right.character());
        output.append(UiSpacingGlyphs.charactersForOffset(-boxWidth + padding));
        for (int line = 1; line <= 3; line++) {
            output.append(TooltipGlyphs.translateLine(lines[line - 1], line, letterSpacing, wordSpacing));
            output.append(UiSpacingGlyphs.charactersForOffset(-TooltipGlyphs.width(lines[line - 1], letterSpacing, wordSpacing)));
        }
        output.append(UiSpacingGlyphs.charactersForOffset(boxWidth - xOffset - padding));
        return output.toString();
    }
}
