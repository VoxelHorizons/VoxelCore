package org.voxelhorizons.colorpicker;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Validated, immutable menu layout and RGB palettes. All slots are raw top-inventory indices. */
public final class ColorPickerSettings {
    public final boolean enabled;
    public final int size;
    public final int inputSlot;
    public final int outputSlot;
    public final int applySlot;
    public final List<Integer> shadeSlots;
    public final List<Palette> palettes;
    public final String javaTitle;
    public final String fallbackTitle;
    public final String bedrockTitle;
    public final String colorMaterial;
    public final String applyMaterial;
    public final String colorName;
    public final String shadeName;
    public final String applyName;
    public final String selectedPrefix;
    public final Map<String, String> messages;

    private ColorPickerSettings(Configuration config) {
        enabled = config.getBoolean("enabled", true);
        size = config.getInt("size", 45);
        if (size < 9 || size > 54 || size % 9 != 0)
            throw new IllegalArgumentException("colorpicker.size must be a multiple of 9 from 9 to 54");
        inputSlot = config.getInt("input-slot", 10);
        outputSlot = config.getInt("output-slot", 16);
        applySlot = config.getInt("apply-slot", 31);
        shadeSlots = Collections.unmodifiableList(slots(config.getIntegerList("shade-slots")));
        if (shadeSlots.isEmpty() || shadeSlots.size() > 9)
            throw new IllegalArgumentException("colorpicker.shade-slots must have 1-9 entries");
        javaTitle = config.getString("titles.java", "&f:offset_-8::colorpicker_{palette}:");
        fallbackTitle = config.getString("titles.fallback", "&8Color Picker - {palette}");
        bedrockTitle = config.getString("titles.bedrock", "&8Color Picker - {palette}");
        colorMaterial = config.getString("buttons.color-material", "FIREWORK_STAR");
        applyMaterial = config.getString("buttons.apply-material", "ANVIL");
        colorName = config.getString("buttons.color-name", "&e{palette}");
        shadeName = config.getString("buttons.shade-name", "&f{palette} &7- &f{hex}");
        applyName = config.getString("buttons.apply-name", "&aPaint Item");
        selectedPrefix = config.getString("buttons.selected-prefix", "&e▶ ");
        ConfigurationSection section = config.getConfigurationSection("palettes");
        if (section == null || section.getKeys(false).isEmpty())
            throw new IllegalArgumentException("colorpicker.palettes cannot be empty");
        List<Palette> parsed = new ArrayList<Palette>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection value = section.getConfigurationSection(key);
            if (value == null) throw new IllegalArgumentException("colorpicker.palettes." + key + " must be a section");
            List<String> shades = value.getStringList("shades");
            if (shades.isEmpty() || shades.size() > shadeSlots.size())
                throw new IllegalArgumentException("colorpicker.palettes." + key + ".shades must have 1-"
                        + shadeSlots.size() + " hex entries");
            List<Integer> colors = new ArrayList<Integer>();
            for (String hex : shades) colors.add(Integer.valueOf(parseHex(hex)));
            parsed.add(new Palette(key, value.getString("name", key), value.getInt("slot", -1), colors));
        }
        if (parsed.size() > 9) throw new IllegalArgumentException("colorpicker supports at most nine primary palettes");
        palettes = Collections.unmodifiableList(parsed);
        Set<Integer> occupied = new HashSet<Integer>();
        addSlot(occupied, inputSlot, "input-slot");
        addSlot(occupied, outputSlot, "output-slot");
        addSlot(occupied, applySlot, "apply-slot");
        for (int slot : shadeSlots) addSlot(occupied, slot, "shade-slots");
        for (Palette palette : palettes) addSlot(occupied, palette.slot, "palettes." + palette.id + ".slot");

        Map<String, String> texts = new LinkedHashMap<String, String>();
        ConfigurationSection messagesConfig = config.getConfigurationSection("messages");
        if (messagesConfig != null) {
            for (String key : messagesConfig.getKeys(false))
                texts.put(key, messagesConfig.getString(key, ""));
        }
        messages = Collections.unmodifiableMap(texts);
    }

    public static ColorPickerSettings from(Configuration configuration) {
        return new ColorPickerSettings(configuration);
    }

    public static int parseHex(String input) {
        String hex = input == null ? "" : input.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (!hex.matches("[0-9a-fA-F]{6}"))
            throw new IllegalArgumentException("Expected a six-digit RGB colour (#RRGGBB), got '" + input + "'");
        return Integer.parseInt(hex, 16);
    }

    public static String formatHex(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    private void addSlot(Set<Integer> occupied, int slot, String name) {
        if (slot < 0 || slot >= size)
            throw new IllegalArgumentException("colorpicker." + name + " has an out-of-range slot: " + slot);
        if (!occupied.add(Integer.valueOf(slot)))
            throw new IllegalArgumentException("colorpicker." + name + " overlaps slot " + slot);
    }

    private static List<Integer> slots(List<Integer> values) {
        return new ArrayList<Integer>(values);
    }

    public static final class Palette {
        public final String id;
        public final String name;
        public final int slot;
        public final List<Integer> shades;

        private Palette(String id, String name, int slot, List<Integer> shades) {
            this.id = id;
            this.name = name;
            this.slot = slot;
            this.shades = Collections.unmodifiableList(new ArrayList<Integer>(shades));
        }
    }
}
