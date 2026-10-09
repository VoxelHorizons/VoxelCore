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

    /**
     * Translate text into vertically positioned tooltip glyphs, optionally tightening
     * horizontal spacing. Color codes are preserved, and custom icons retain their
     * original advance. Letter spacing only applies inside uninterrupted ASCII words.
     */
    public static String translateLine(String input, int line) {
        return translateLine(input, line, 0, 0);
    }

    public static String translateLine(String input, int line, int letterSpacing, int wordSpacing) {
        if (input == null || input.isEmpty()) return "";
        int base = lineBase(line);
        StringBuilder output = new StringBuilder(input.length());
        boolean previousAscii = false;
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
                if (previousAscii) output.append(UiSpacingGlyphs.charactersForOffset(letterSpacing));
                output.append((char) (base + codePoint));
                previousAscii = true;
            } else if (codePoint == 32) {
                output.append(' ');
                output.append(UiSpacingGlyphs.charactersForOffset(wordSpacing));
                previousAscii = false;
            } else {
                // Non-ASCII characters (including authored icons) are not kerned.
                output.appendCodePoint(codePoint);
                previousAscii = false;
            }
        }
        return output.toString();
    }

    /** Width in resource-pack font pixels, ignoring legacy formatting codes. */
    public static int width(String input) {
        return width(input, 0, 0);
    }

    /** Measure the exact same added offsets that translateLine emits. */
    public static int width(String input, int letterSpacing, int wordSpacing) {
        if (input == null || input.isEmpty()) return 0;
        int width = 0;
        boolean previousAscii = false;
        for (int index = 0; index < input.length();) {
            char current = input.charAt(index);
            if (current == '\u00A7' && index + 1 < input.length()) {
                index += 2;
                continue;
            }
            int codePoint = input.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint >= 33 && codePoint <= 126) {
                width += ASCII_ADVANCES[codePoint - 32] + (previousAscii ? letterSpacing : 0);
                previousAscii = true;
            } else if (codePoint == 32) {
                width += ASCII_ADVANCES[0] + wordSpacing;
                previousAscii = false;
            } else {
                width += 4; // Conservative non-ASCII fallback, matching existing behavior.
                previousAscii = false;
            }
        }
        return width;
    }
}
