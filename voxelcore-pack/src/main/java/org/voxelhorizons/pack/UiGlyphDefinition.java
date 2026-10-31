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
    private final int bedrockHeight;
    private final double bedrockScale;

    UiGlyphDefinition(ContentID id, String texture, int scaleRatio, int yPosition,
                      int advance, boolean gui, int codePoint) {
        this(id, texture, scaleRatio, yPosition, advance, gui, codePoint, 0, 0.0D);
    }

    UiGlyphDefinition(ContentID id, String texture, int scaleRatio, int yPosition,
                      int advance, boolean gui, int codePoint, int bedrockHeight, double bedrockScale) {
        this.id = id;
        this.texture = texture;
        this.scaleRatio = scaleRatio;
        this.yPosition = yPosition;
        this.advance = advance;
        this.gui = gui;
        this.codePoint = codePoint;
        this.bedrockHeight = bedrockHeight;
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
    /** Exact full height in pixels baked into the Bedrock glyph page, or 0 to derive it. */
    public int bedrockHeight() { return bedrockHeight; }
    /** Multiplier applied to {@link #scaleRatio()} for the Bedrock glyph, or 0 for none. */
    public double bedrockScale() { return bedrockScale; }
    public String character() { return new String(Character.toChars(codePoint)); }
    public String escapedCodePoint() { return String.format("U+%04X", codePoint); }

    @Override public String toString() {
        return id + " (" + escapedCodePoint() + ", scale_ratio=" + scaleRatio + ", y_position=" + yPosition
                + ", advance=" + advance + ", gui=" + gui + ")";
    }
}
