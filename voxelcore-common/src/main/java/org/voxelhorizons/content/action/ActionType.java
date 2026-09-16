package org.voxelhorizons.content.action;

import java.util.Locale;

/** Built-in actions understood by the VoxelCore runtime. */
public enum ActionType {
    SET_BLOCK,
    REMOVE_BLOCK,
    COMMAND,
    GIVE_ITEM,
    DROP_ITEM,
    MESSAGE,
    CANCEL;

    public static ActionType parse(String value) {
        if (value == null) throw new IllegalArgumentException("Action type cannot be null");
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown action type '" + value + "'");
        }
    }
}
