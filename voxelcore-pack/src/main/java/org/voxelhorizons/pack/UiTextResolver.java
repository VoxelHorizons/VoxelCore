package org.voxelhorizons.pack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves stable UI and pixel-offset aliases into resource-pack font characters. */
public final class UiTextResolver {
    public static final char COLOR_CHAR = '\u00A7';
    private static final Pattern OFFSET = Pattern.compile(":offset_(-?\\d+):");

    private final UiGlyphRegistry glyphs;

    public UiTextResolver(UiGlyphRegistry glyphs) {
        if (glyphs == null) throw new IllegalArgumentException("glyphs cannot be null");
        this.glyphs = glyphs;
    }

    /**
     * Resolves UI aliases with an explicit white legacy color prefix and resolves offsets.
     * Unknown or out-of-range placeholders are preserved so configuration mistakes remain visible.
     */
    public String resolve(String input) {
        if (input == null || input.indexOf(':') < 0) return input;
        String resolved = glyphs.resolveAliases(input, true);
        Matcher matcher = OFFSET.matcher(resolved);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(0);
            try {
                int offset = Integer.parseInt(matcher.group(1));
                replacement = UiSpacingGlyphs.charactersForOffset(offset);
            } catch (IllegalArgumentException ignored) {
                // Preserve malformed and out-of-range placeholders for diagnosis.
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }
}
