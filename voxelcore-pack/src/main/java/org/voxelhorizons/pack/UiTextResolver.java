package org.voxelhorizons.pack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves stable UI and pixel-offset aliases into resource-pack font characters. */
public final class UiTextResolver {
    public static final char COLOR_CHAR = '\u00A7';
    private static final Pattern OFFSET = Pattern.compile(":offset_(-?\\d+):");

    private final UiGlyphRegistry glyphs;
    private final ContainerGuiLayout containerGuiLayout;

    public UiTextResolver(UiGlyphRegistry glyphs) {
        this(glyphs, ContainerGuiLayout.defaults());
    }

    public UiTextResolver(UiGlyphRegistry glyphs, ContainerGuiLayout containerGuiLayout) {
        if (glyphs == null) throw new IllegalArgumentException("glyphs cannot be null");
        this.glyphs = glyphs;
        this.containerGuiLayout = containerGuiLayout == null ? ContainerGuiLayout.defaults() : containerGuiLayout;
    }

    /**
     * Resolves UI aliases with an explicit white legacy color prefix and resolves offsets.
     * Unknown or out-of-range placeholders are preserved so configuration mistakes remain visible.
     */
    public String resolve(String input) {
        return resolve(input, true, true);
    }

    /**
     * Resolves independently authorized inline and GUI placeholders.
     * Pixel offsets are GUI positioning controls and therefore require GUI authorization.
     */
    public String resolve(String input, boolean allowInline, boolean allowGui) {
        if (input == null || input.indexOf(':') < 0) return input;
        String resolved = allowGui ? ContainerGuiGlyphs.resolveAliases(input, true, containerGuiLayout) : input;
        resolved = glyphs.resolveAliases(resolved, true, allowInline, allowGui);
        if (!allowGui) return resolved;
        Matcher matcher = OFFSET.matcher(resolved);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(0);
            try {
                int offset = Integer.parseInt(matcher.group(1));
                if (offset < -UiSpacingGlyphs.maxOffset() || offset > UiSpacingGlyphs.maxOffset()) {
                    throw new IllegalArgumentException("Offset is outside the public placeholder range");
                }
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
