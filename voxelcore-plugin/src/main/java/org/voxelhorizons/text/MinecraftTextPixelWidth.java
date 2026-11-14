package org.voxelhorizons.text;

import org.voxelhorizons.pack.UiSpacingGlyphs;

/** Width of legacy-formatted Minecraft:default text, in client font pixels. */
final class MinecraftTextPixelWidth {
    private MinecraftTextPixelWidth() {}

    static int width(String input) {
        if (input == null || input.isEmpty()) return 0;
        int result = 0;
        boolean bold = false;
        for (int i = 0; i < input.length();) {
            char c = input.charAt(i);
            if ((c == '\u00a7' || c == '&') && i + 1 < input.length()) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                if (code == '#' && c == '&' && i + 8 <= input.length()
                        && input.substring(i + 2, i + 8).matches("[0-9a-fA-F]{6}")) {
                    bold = false;
                    i += 8;
                    continue;
                }
                if ("0123456789abcdefklmnorx".indexOf(code) >= 0) {
                    if (code == 'l') bold = true;
                    else if (code == 'r' || "0123456789abcdefx".indexOf(code) >= 0) bold = false;
                    i += 2;
                    continue;
                }
            }
            // MiniMessage tags are formatting, not visible characters.
            if (c == '<') {
                int end = input.indexOf('>', i + 1);
                if (end != -1 && end - i <= 96) {
                    String tag = input.substring(i + 1, end).toLowerCase(java.util.Locale.ROOT);
                    if (tag.matches("/?[a-z_!][a-z0-9_:#/!=. -]*")) {
                        if (tag.equals("bold") || tag.equals("b")) bold = true;
                        if (tag.equals("/bold") || tag.equals("/b") || tag.equals("reset")) bold = false;
                        i = end + 1;
                        continue;
                    }
                }
            }
            int cp = input.codePointAt(i);
            i += Character.charCount(cp);
            Integer spacing = UiSpacingGlyphs.advances().get(Integer.valueOf(cp));
            if (spacing != null) {
                result += spacing.intValue();
            } else {
                int advance = advance(cp);
                result += advance + (bold && advance > 0 && cp != ' ' ? 1 : 0);
            }
        }
        return result;
    }

    /** Vanilla Mojangles advances, including the normal one-pixel gap. */
    private static int advance(int codePoint) {
        if (codePoint == ' ') return 4;
        if (codePoint < 33 || codePoint > 126) return 8; // Unicode fallback estimate.
        switch (codePoint) {
            case '!': case '\'': case ',': case '.': case ':': case ';':
            case 'i': case '|': return 2;
            case 'l': case '`': return 3;
            case '"': case '(': case ')': case '*': case '[': case ']':
            case 'I': case 't': case '{': case '}': return 4;
            case '<': case '>': case 'f': case 'k': return 5;
            case '@': case '~': return 7;
            default: return 6;
        }
    }
}
