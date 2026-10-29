package org.voxelhorizons.pack;

import org.voxelhorizons.content.ContentID;

/** Immutable resolved UI bitmap glyph definition. */
public final class UiGlyphDefinition {
    private final ContentID id;
    private final String texture;
    private final int scaleRatio;
    private final int yPosition;
    private final int advance;
    private final boolean gui;
    private final int codePoint;
    private final double bedrockScale;

    UiGlyphDefinition(ContentID id, String texture, int scaleRatio, int yPosition,
                      int advance, boolean gui, int codePoint) {
        this(id, texture, scaleRatio, yPosition, advance, gui, codePoint, 1.0D);
    }

    UiGlyphDefinition(ContentID id, String texture, int scaleRatio, int yPosition,
                      int advance, boolean gui, int codePoint, double bedrockScale) {
        this.id = id;
        this.texture = texture;
        this.scaleRatio = scaleRatio;
        this.yPosition = yPosition;
        this.advance = advance;
        this.gui = gui;
        this.codePoint = codePoint;
        this.bedrockScale = bedrockScale;
    }

    public ContentID id() { return id; }
    public String texture() { return texture; }
    public int scaleRatio() { return scaleRatio; }
    public int yPosition() { return yPosition; }
    public int advance() { return advance; }
    public boolean gui() { return gui; }
    public int height() { return scaleRatio; }
    public int ascent() { return yPosition; }
    public int codePoint() { return codePoint; }
    /** Bedrock-only bitmap scale; Java font metrics and rendering remain unchanged. */
    public double bedrockScale() { return bedrockScale; }
    public String character() { return new String(Character.toChars(codePoint)); }
    public String escapedCodePoint() { return String.format("U+%04X", codePoint); }

    @Override public String toString() {
        return id + " (" + escapedCodePoint() + ", scale_ratio=" + scaleRatio + ", y_position=" + yPosition
                + ", advance=" + advance + ", gui=" + gui + ", bedrock_scale=" + bedrockScale + ")";
    }
}
