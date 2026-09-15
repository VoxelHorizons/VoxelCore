package org.voxelhorizons.pack;

import org.voxelhorizons.content.ContentID;

/** Immutable resolved UI bitmap glyph definition. */
public final class UiGlyphDefinition {
    private final ContentID id;
    private final String texture;
    private final int scaleRatio;
    private final int yPosition;
    private final int codePoint;

    UiGlyphDefinition(ContentID id, String texture, int scaleRatio, int yPosition, int codePoint) {
        this.id = id;
        this.texture = texture;
        this.scaleRatio = scaleRatio;
        this.yPosition = yPosition;
        this.codePoint = codePoint;
    }

    public ContentID id() { return id; }
    public String texture() { return texture; }
    public int scaleRatio() { return scaleRatio; }
    public int yPosition() { return yPosition; }
    public int height() { return scaleRatio; }
    public int ascent() { return yPosition; }
    public int codePoint() { return codePoint; }
    public String character() { return new String(Character.toChars(codePoint)); }
    public String escapedCodePoint() { return String.format("U+%04X", codePoint); }

    @Override public String toString() {
        return id + " (" + escapedCodePoint() + ", scale_ratio=" + scaleRatio + ", y_position=" + yPosition + ")";
    }
}
