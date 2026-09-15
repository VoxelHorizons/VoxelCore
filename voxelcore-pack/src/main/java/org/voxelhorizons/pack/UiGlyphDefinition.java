package org.voxelhorizons.pack;

import org.voxelhorizons.content.ContentID;

/** Immutable resolved UI bitmap glyph definition. */
public final class UiGlyphDefinition {
    private final ContentID id;
    private final String texture;
    private final int rows;
    private final int height;
    private final int ascent;
    private final int codePoint;

    UiGlyphDefinition(ContentID id, String texture, int rows, int height, int ascent, int codePoint) {
        this.id = id;
        this.texture = texture;
        this.rows = rows;
        this.height = height;
        this.ascent = ascent;
        this.codePoint = codePoint;
    }

    public ContentID id() { return id; }
    public String texture() { return texture; }
    public int rows() { return rows; }
    public int height() { return height; }
    public int ascent() { return ascent; }
    public int codePoint() { return codePoint; }
    public String character() { return new String(Character.toChars(codePoint)); }
    public String escapedCodePoint() { return String.format("U+%04X", codePoint); }

    @Override public String toString() {
        return id + " (" + escapedCodePoint() + ", rows=" + rows + ", height=" + height + ", ascent=" + ascent + ")";
    }
}
