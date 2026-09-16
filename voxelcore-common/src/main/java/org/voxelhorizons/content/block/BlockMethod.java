package org.voxelhorizons.content.block;

import java.util.Locale;

/** Authored carrier family. AUTO is resolved to a stable concrete method during allocation. */
public enum BlockMethod {
    AUTO,
    SOLID,
    MUSHROOM,
    TRANSPARENT,
    WIRE,
    FIRE;

    public static BlockMethod parse(String value) {
        if (value == null) return AUTO;
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown block method '" + value + "'");
        }
    }
}
