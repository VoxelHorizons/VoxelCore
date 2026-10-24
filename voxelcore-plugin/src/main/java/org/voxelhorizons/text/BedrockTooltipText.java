package org.voxelhorizons.text;

/**
 * Bedrock alternative for the Java glyph-framed tooltip (B16). Bedrock cannot draw the
 * private-use background/spacing glyphs the Java layout composes, and its subtitle is a
 * single line, so the non-blank lines are joined with a separator and private-use
 * characters are removed. Legacy colour codes are kept.
 */
public final class BedrockTooltipText {
    static final String SEPARATOR = " §8•§r ";

    private BedrockTooltipText() {}

    public static String subtitle(String... lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (line == null) continue;
            String clean = stripPrivateUse(line).replaceAll("[ \\t]{2,}", " ").trim();
            if (clean.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim().isEmpty()) continue;
            if (out.length() > 0) out.append(SEPARATOR);
            out.append(clean);
        }
        return out.toString();
    }

    static String stripPrivateUse(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            boolean privateUse = (codePoint >= 0xE000 && codePoint <= 0xF8FF) || codePoint >= 0xF0000;
            if (!privateUse) out.appendCodePoint(codePoint);
        }
        return out.toString();
    }
}
