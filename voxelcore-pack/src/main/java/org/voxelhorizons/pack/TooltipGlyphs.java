package org.voxelhorizons.pack;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Compiler-owned glyph allocation and layout helpers for three-line popup tooltips.
 *
 * <p>The three text rows are mapped to private-use copies of the vanilla ASCII
 * atlas at different ascents. Keeping the variants in the default font lets the
 * runtime use Bukkit's cross-version legacy title API without needing Adventure
 * font components.</p>
 */
public final class TooltipGlyphs {
    public static final int LINE_1_BASE = 0xF400;
    public static final int LINE_2_BASE = 0xF500;
    public static final int LINE_3_BASE = 0xF600;

    public static final int BACKGROUND_LEFT = 0xF700;
    public static final int BACKGROUND_CENTER = 0xF701;
    public static final int BACKGROUND_RIGHT = 0xF702;
    public static final int BACKGROUND_RIGHT_OFFSET = 0xF703;

    public static final int DEFAULT_X_OFFSET = 55;
    public static final int HORIZONTAL_PADDING = 4;

    private static final int[] ASCII_ADVANCES = {
            4,2,3,4,4,4,4,2,3,3,3,4,2,4,2,4,
            4,4,4,4,4,4,4,4,4,4,2,2,3,4,3,4,
            4,4,4,4,4,4,4,4,4,3,4,4,4,4,4,4,
            4,4,4,4,4,4,4,4,4,4,4,3,4,3,4,4,
            2,4,4,4,4,4,3,4,4,2,4,3,2,4,4,4,
            4,4,4,4,3,4,4,4,4,4,4,3,2,3,4
    };

    private static final Set<Integer> RESERVED;

    static {
        Set<Integer> reserved = new LinkedHashSet<Integer>();
        for (int cp = LINE_1_BASE; cp <= LINE_3_BASE + 0xFF; cp++) {
            reserved.add(Integer.valueOf(cp));
        }
        reserved.add(Integer.valueOf(BACKGROUND_LEFT));
        reserved.add(Integer.valueOf(BACKGROUND_CENTER));
        reserved.add(Integer.valueOf(BACKGROUND_RIGHT));
        reserved.add(Integer.valueOf(BACKGROUND_RIGHT_OFFSET));
        RESERVED = Collections.unmodifiableSet(reserved);
    }

    private TooltipGlyphs() {}

    public static Set<Integer> reservedCodePoints() { return RESERVED; }

    public static int lineBase(int line) {
        switch (line) {
            case 1: return LINE_1_BASE;
            case 2: return LINE_2_BASE;
            case 3: return LINE_3_BASE;
            default: throw new IllegalArgumentException("Tooltip line must be 1, 2 or 3");
        }
    }

    public static int lineAscent(int line) {
        switch (line) {
            case 1: return 4;
            case 2: return -1;
            case 3: return -6;
            default: throw new IllegalArgumentException("Tooltip line must be 1, 2 or 3");
        }
    }

    /**
     * Returns sixteen 16-character rows matching minecraft:font/ascii.png.
     * Printable ASCII is remapped to compiler-reserved private-use characters.
     */
    public static String[] fontRows(int line) {
        int base = lineBase(line);
        String[] rows = new String[16];
        for (int row = 0; row < 16; row++) {
            StringBuilder value = new StringBuilder(16);
            for (int column = 0; column < 16; column++) {
                int source = row * 16 + column;
                if (source >= 32 && source <= 126 && source != 32) {
                    value.append((char) (base + source));
                } else {
                    value.append('\0');
                }
            }
            rows[row] = value.toString();
        }
        return rows;
    }

    /** Maps printable ASCII onto the requested vertically-positioned tooltip row. */
    public static String translateLine(String input, int line) {
        if (input == null || input.isEmpty()) return "";
        int base = lineBase(line);
        StringBuilder output = new StringBuilder(input.length());
        for (int index = 0; index < input.length();) {
            char current = input.charAt(index);
            if (current == '\u00A7' && index + 1 < input.length()) {
                output.append(current).append(input.charAt(index + 1));
                index += 2;
                continue;
            }
            int codePoint = input.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint >= 33 && codePoint <= 126) {
                output.append((char) (base + codePoint));
            } else {
                // Spaces remain ordinary spaces so they retain the vanilla 4 px advance.
                // Non-ASCII glyphs are preserved for authored icon/font providers.
                output.appendCodePoint(codePoint);
            }
        }
        return output.toString();
    }

    /** Width in resource-pack font pixels, ignoring legacy formatting codes. */
    public static int width(String input) {
        if (input == null || input.isEmpty()) return 0;
        int width = 0;
        for (int index = 0; index < input.length();) {
            char current = input.charAt(index);
            if (current == '\u00A7' && index + 1 < input.length()) {
                index += 2;
                continue;
            }
            int codePoint = input.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint >= 32 && codePoint <= 126) {
                width += ASCII_ADVANCES[codePoint - 32];
            } else {
                // Conservative fallback for authored/non-ASCII glyphs.
                width += 4;
            }
        }
        return width;
    }

    public static String backgroundForWidth(int requestedWidth) {
        int width = Math.max(4, requestedWidth);
        boolean odd = (width & 1) != 0;
        int rightAdvance = odd ? 3 : 2;
        int centers = Math.max(0, (width - 2 - rightAdvance) / 2);
        StringBuilder output = new StringBuilder(centers + 2);
        output.append((char) BACKGROUND_LEFT);
        for (int i = 0; i < centers; i++) output.append((char) BACKGROUND_CENTER);
        output.append((char) (odd ? BACKGROUND_RIGHT_OFFSET : BACKGROUND_RIGHT));
        return output.toString();
    }

    public static int backgroundWidth(String background) {
        if (background == null || background.isEmpty()) return 0;
        int width = 0;
        for (int i = 0; i < background.length(); i++) {
            char glyph = background.charAt(i);
            if (glyph == BACKGROUND_LEFT || glyph == BACKGROUND_CENTER || glyph == BACKGROUND_RIGHT) width += 2;
            else if (glyph == BACKGROUND_RIGHT_OFFSET) width += 3;
        }
        return width;
    }

    /**
     * Creates one subtitle string whose background and three vertically offset
     * text rows overlap while retaining a stable final advance for centering.
     */
    public static String compose(String line1, String line2, String line3, int xOffset) {
        String[] lines = {line1 == null ? "" : line1, line2 == null ? "" : line2, line3 == null ? "" : line3};
        int maxWidth = Math.max(width(lines[0]), Math.max(width(lines[1]), width(lines[2])));
        int requested = maxWidth + (HORIZONTAL_PADDING * 2);
        String background = backgroundForWidth(requested);
        int boxWidth = backgroundWidth(background);

        StringBuilder output = new StringBuilder();
        output.append(UiSpacingGlyphs.charactersForOffset(xOffset));
        output.append('\u00A7').append('f').append(background);
        output.append(UiSpacingGlyphs.charactersForOffset(-boxWidth));
        output.append(UiSpacingGlyphs.charactersForOffset(HORIZONTAL_PADDING));

        for (int line = 1; line <= 3; line++) {
            String value = lines[line - 1];
            output.append(translateLine(value, line));
            output.append(UiSpacingGlyphs.charactersForOffset(-width(value)));
        }

        // Net subtitle advance remains exactly boxWidth, so Minecraft centers the
        // box predictably while xOffset moves the rendered pixels to the right.
        output.append(UiSpacingGlyphs.charactersForOffset(boxWidth - xOffset - HORIZONTAL_PADDING));
        return output.toString();
    }
}
