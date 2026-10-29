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

    @Test
    public void migratesSingleTooltipStyleWithoutLosingOperatorOverrides() throws Exception {
        YamlConfiguration current = new YamlConfiguration();
        current.loadFromString("version: 5\nui:\n  tooltip:\n    left: 'voxel:custom_left'\n    tile_overlap: 0\n    x_offset: 73\n  tooltips:\n    default:\n      center: 'voxel:custom_center'\n    danger:\n      left: 'voxel:danger_left'\n");
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.loadFromString("version: 6\nui:\n  tooltips:\n    default:\n      left: 'voxel:tooltip_left'\n      center: 'voxel:tooltip_center'\n      right: 'voxel:tooltip_right'\n      tile_overlap: 1\n      horizontal_padding: 4\n      x_offset: 55\n");

        ConfigMigrationSupport.merge(current, defaults, 6);

        assertEquals(6, current.getInt("version"));
        assertEquals("voxel:custom_left", current.getString("ui.tooltips.default.left"));
        assertEquals("voxel:custom_center", current.getString("ui.tooltips.default.center"));
        assertEquals("voxel:tooltip_right", current.getString("ui.tooltips.default.right"));
        assertEquals(0, current.getInt("ui.tooltips.default.tile_overlap"));
        assertEquals(73, current.getInt("ui.tooltips.default.x_offset"));
        assertEquals("voxel:danger_left", current.getString("ui.tooltips.danger.left"));
        assertTrue(!current.contains("ui.tooltip"));
    }
    @Test
    public void addsPhysicalContainerTitlesWhileKeepingLegacyChestConfiguration() throws Exception {
        YamlConfiguration current = new YamlConfiguration();
        current.loadFromString("version: 9\nui:\n  chest_titles:\n"
                + "    enabled: true\n    single: '&f:custom_chest:'\n    double: '&f:custom_large:'\n");
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.loadFromString("version: 10\nui:\n  chest_titles:\n"
                + "    enabled: false\n    single: ''\n    double: ''\n"
                + "  container_titles:\n    enabled: false\n"
                + "    chest:\n      single: ''\n      double: ''\n    barrel: ''\n");

        ConfigMigrationSupport.merge(current, defaults, 10);

        assertEquals(10, current.getInt("version"));
        assertTrue(current.getBoolean("ui.chest_titles.enabled"));
        assertEquals("&f:custom_chest:", current.getString("ui.chest_titles.single"));
        assertEquals("&f:custom_large:", current.getString("ui.chest_titles.double"));
        assertTrue(current.contains("ui.container_titles.barrel"));
        assertEquals("", current.getString("ui.container_titles.barrel"));
        assertTrue(!current.getBoolean("ui.container_titles.enabled"));
    }

    @Test
    public void addsBrowserTitleDefaultsWhilePreservingExistingUiSettings() throws Exception {
        YamlConfiguration current = new YamlConfiguration();
        current.loadFromString("version: 6\nui:\n  chest_titles:\n    single: '&eExisting title'\n"
                + "  browser:\n    titles:\n      items: '&f:my_custom_items:'\n");
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.loadFromString("version: 7\nui:\n  browser:\n    titles:\n"
                + "      items: 'VC Items {page}/{pages}'\n"
                + "      blocks: 'VC Blocks {page}/{pages}'\n"
                + "    page_titles:\n      items: {}\n      blocks: {}\n");

        ConfigMigrationSupport.merge(current, defaults, 7);

        assertEquals(7, current.getInt("version"));
        assertEquals("&eExisting title", current.getString("ui.chest_titles.single"));
        assertEquals("&f:my_custom_items:", current.getString("ui.browser.titles.items"));
        assertEquals("VC Blocks {page}/{pages}", current.getString("ui.browser.titles.blocks"));
        YamlConfiguration persisted = new YamlConfiguration();
        persisted.loadFromString(current.saveToString());
        assertEquals("&f:my_custom_items:", persisted.getString("ui.browser.titles.items"));
        assertEquals("VC Blocks {page}/{pages}", persisted.getString("ui.browser.titles.blocks"));
    }

}
