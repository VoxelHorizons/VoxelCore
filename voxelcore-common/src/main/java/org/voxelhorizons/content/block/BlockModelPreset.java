package org.voxelhorizons.content.block;

import java.util.Locale;

public enum BlockModelPreset {
    CUBE_ALL,
    CUBE_COLUMN,
    CUBE;

    public static BlockModelPreset parse(String value) {
        if (value == null) return CUBE_ALL;
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown block model preset '" + value + "'");
        }
    }
}
