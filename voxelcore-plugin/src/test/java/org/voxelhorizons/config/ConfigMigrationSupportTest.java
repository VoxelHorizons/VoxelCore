package org.voxelhorizons.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConfigMigrationSupportTest {
    @Test
    public void preservesExistingValuesAndAddsNewDefaults() throws Exception {
        YamlConfiguration current = new YamlConfiguration();
        current.loadFromString("version: 1\ngame:\n  bedrock_support: true\noperator_value: keep-me\n");
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.loadFromString("version: 2\ngame:\n  bedrock_support: false\n  new_feature: true\n");

        ConfigMigrationSupport.merge(current, defaults, 2);

        assertEquals(2, current.getInt("version"));
        assertTrue(current.getBoolean("game.bedrock_support"));
        assertTrue(current.getBoolean("game.new_feature"));
        assertEquals("keep-me", current.getString("operator_value"));

        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(current.saveToString());
        assertTrue(reloaded.getBoolean("game.bedrock_support"));
        assertTrue(reloaded.getBoolean("game.new_feature"));
        assertEquals("keep-me", reloaded.getString("operator_value"));
    }
}
