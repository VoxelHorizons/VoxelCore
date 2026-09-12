package org.voxelhorizons.config;

import org.bukkit.configuration.file.FileConfiguration;

public final class ConfigMigrationSupport {
    private ConfigMigrationSupport() {}

    /** Adds new defaults while preserving every value already authored by the operator. */
    public static FileConfiguration merge(FileConfiguration current, FileConfiguration defaults, int version) {
        if (current == null) throw new IllegalArgumentException("current config cannot be null");
        if (defaults == null) throw new IllegalArgumentException("default config cannot be null");
        current.setDefaults(defaults);
        current.options().copyDefaults(true);
        current.set("version", Integer.valueOf(version));
        return current;
    }
}
