package org.voxelhorizons.pack;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Compiler-owned glyphs used to visually restore vanilla chest tops after container GUI replacement. */
public final class ContainerGuiGlyphs {
    public static final int GENERIC_27_TOP = 0xF710;
    public static final int GENERIC_54_TOP = 0xF711;

    public static final int ADVANCE = 177;
    public static final int ASCENT = 13;

    private static final Set<Integer> RESERVED = Collections.unmodifiableSet(
            new LinkedHashSet<Integer>(Arrays.asList(
                    Integer.valueOf(GENERIC_27_TOP),
                    Integer.valueOf(GENERIC_54_TOP))));

    private ContainerGuiGlyphs() {}

    public static Set<Integer> reservedCodePoints() { return RESERVED; }

    public static String resolveAliases(String input, boolean forceWhite) {
        if (input == null || input.indexOf(':') < 0) return input;
        String prefix = forceWhite ? "\u00A7f" : "";
        String rewind = UiSpacingGlyphs.charactersForOffset(-ADVANCE);
        return input
                .replace(":generic_27_top:", prefix + new String(Character.toChars(GENERIC_27_TOP)) + rewind)
                .replace(":generic_54_top:", prefix + new String(Character.toChars(GENERIC_54_TOP)) + rewind);
    }
}
