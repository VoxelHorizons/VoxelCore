package org.voxelhorizons.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;

public final class ConfigMigrationSupport {
    private ConfigMigrationSupport() {}

    /** Adds new defaults while preserving every value already authored by the operator. */
    public static FileConfiguration merge(FileConfiguration current, FileConfiguration defaults, int version) {
        if (current == null) throw new IllegalArgumentException("current config cannot be null");
        if (defaults == null) throw new IllegalArgumentException("default config cannot be null");
        // Migrate v5's single tooltip into the v6 default variant before copying defaults.
        // Never overwrite an explicitly authored ui.tooltips.default property.
        ConfigurationSection legacy = current.getConfigurationSection("ui.tooltip");
        if (legacy != null) {
            for (String key : legacy.getKeys(false)) {
                String target = "ui.tooltips.default." + key;
                if (!current.contains(target, true)) {
                    current.set(target, legacy.get(key));
                }
            }
            current.set("ui.tooltip", null);
        }
        current.setDefaults(defaults);
        current.options().copyDefaults(true);
        current.set("version", Integer.valueOf(version));
        return current;
    }
}
