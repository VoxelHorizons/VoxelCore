package org.voxelhorizons.colorpicker;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class ColorPickerSettingsTest {
    private static YamlConfiguration defaults() {
        InputStream resource = ColorPickerSettingsTest.class.getResourceAsStream("/colorpicker.yml");
        if (resource == null) throw new AssertionError("Bundled colorpicker.yml missing");
        try {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        } finally {
            try { resource.close(); } catch (java.io.IOException ignored) { }
        }
    }

    @Test public void bundledPalettesMatchNineUploadedTextures() {
        ColorPickerSettings settings = ColorPickerSettings.from(defaults());
        assertTrue(settings.enabled);
        assertEquals(54, settings.size);
        assertEquals(19, settings.inputSlot);
        assertEquals(25, settings.outputSlot);
        assertEquals("", settings.selectedShadeMaterial);
        assertEquals("&f:offset_-8::colorpicker_background:", settings.title);
        assertEquals(9, settings.palettes.size());
        assertEquals(9, settings.shadeSlots.size());
        int[][] expected = {
            {0x991E1E, 0x3A2525}, {0xCC6A28, 0x4E3D31}, {0xCCB028, 0x4E4931},
            {0xCC28B6, 0x4E314A}, {0xCCCCCC, 0x28282A}, {0x59CC28, 0x3A4E31},
            {0x9828CC, 0x45314E}, {0x2841CC, 0x31364E}, {0x28CCCC, 0x314E4E}
        };
        int[] slots = {12,13,14,21,22,30,23,32,31};
        for (int i = 0; i < expected.length; i++) {
            ColorPickerSettings.Palette p = settings.palettes.get(i);
            assertEquals(9, p.shades.size());
            assertEquals(slots[i], p.slot);
            assertEquals(expected[i][0], p.shades.get(0).intValue());
            assertEquals(expected[i][0], p.buttonColor);
            assertEquals(expected[i][1], p.shades.get(8).intValue());
        }
    }

    @Test public void primaryButtonColorOverridesFirstShadeWithoutChangingShades() {
        YamlConfiguration config = defaults();
        config.set("palettes.red.button-color", "#1234AB");
        ColorPickerSettings settings = ColorPickerSettings.from(config);
        assertEquals(0x1234AB, settings.palettes.get(0).buttonColor);
        assertEquals(0x991E1E, settings.palettes.get(0).shades.get(0).intValue());
    }

    @Test public void invalidPrimaryButtonColorIsRejected() {
        YamlConfiguration config = defaults();
        config.set("palettes.red.button-color", "not-hex");
        try {
            ColorPickerSettings.from(config);
            fail("Accepted invalid primary tint");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("six-digit RGB"));
        }
    }

    @Test public void singleTitleAppliesWithoutClientSpecificOverrides() {
        YamlConfiguration config = defaults();
        config.set("title", "&f:colorpicker_{palette}:");
        config.set("titles.java", "&cLegacy Java Title");
        config.set("titles.bedrock", "&cLegacy Bedrock Title");
        config.set("titles.fallback", "&cLegacy Fallback Title");
        assertEquals("&f:colorpicker_{palette}:", ColorPickerSettings.from(config).title);
    }

    @Test public void olderJavaTitleIsReadWhenSharedTitleIsMissing() {
        YamlConfiguration config = defaults();
        config.set("title", null);
        config.set("titles.java", "&f:old_palette_{palette}:");
        assertEquals("&f:old_palette_{palette}:", ColorPickerSettings.from(config).title);
    }

    @Test public void blankButtonNamesAndEmptyLoreAreSupported() {
        YamlConfiguration config = defaults();
        config.set("buttons.color-name", "");
        config.set("buttons.color-lore", java.util.Collections.emptyList());
        config.set("buttons.shade-name", "");
        config.set("buttons.shade-lore", "");
        config.set("buttons.selected-prefix", "");
        ColorPickerSettings settings = ColorPickerSettings.from(config);
        assertEquals("", settings.colorName);
        assertEquals("", settings.shadeName);
        assertEquals("", settings.selectedPrefix);
        assertTrue(settings.colorLore.isEmpty());
        assertTrue(settings.shadeLore.isEmpty());
        assertTrue(ColorPickerMenu.formatLore(settings.colorLore, settings.palettes.get(0),
                settings.palettes.get(0).shades.get(0).intValue()).isEmpty());
    }

    @Test public void multiLineLoreReplacesHexWithRgbAndSupportsColourCodes() {
        YamlConfiguration config = defaults();
        config.set("buttons.shade-lore", java.util.Arrays.asList(
                "&e{name}", "&7{hex}", "", "&6{palette}"));
        ColorPickerSettings settings = ColorPickerSettings.from(config);
        assertEquals(java.util.Arrays.asList("§eRed", "§7#991E1E", "§6Red"),
                ColorPickerMenu.formatLore(settings.shadeLore,
                        settings.palettes.get(0), 0x991E1E));
    }

    @Test public void missingLoreSettingKeepsPreviousHexDefault() {
        YamlConfiguration config = defaults();
        config.set("buttons.color-lore", null);
        config.set("buttons.shade-lore", null);
        ColorPickerSettings settings = ColorPickerSettings.from(config);
        assertEquals(java.util.Collections.singletonList("&7{hex}"), settings.colorLore);
        assertEquals(java.util.Collections.singletonList("&7{hex}"), settings.shadeLore);
    }

    @Test public void numericLoreEntryIsRejected() {
        YamlConfiguration config = defaults();
        config.set("buttons.shade-lore", java.util.Arrays.asList(123));
        try {
            ColorPickerSettings.from(config);
            fail("Accepted numeric lore entries");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("entries must be strings"));
        }
    }

    @Test public void customSelectedBorderCanBeConfigured() {
        YamlConfiguration config = defaults();
        config.set("buttons.color-material", "voxel:ui/blank");
        config.set("buttons.selected-shade-material", "voxel:ui/colorpicker_selected");
        ColorPickerSettings settings = ColorPickerSettings.from(config);
        assertEquals("voxel:ui/blank", settings.colorMaterial);
        assertEquals("voxel:ui/colorpicker_selected", settings.selectedShadeMaterial);
    }

    @Test public void customUiButtonAcceptsResourceStyleItemKeys() {
        assertEquals(org.voxelhorizons.content.ContentID.of("voxel", "ui-blank"),
                ColorPickerMenu.parseButtonContentId("voxel:ui/blank"));
        assertEquals(org.voxelhorizons.content.ContentID.of("voxel", "ui-blank"),
                ColorPickerMenu.parseButtonContentId("voxel:ui-blank"));
        assertEquals(org.voxelhorizons.content.ContentID.of("custompack", "paint-icon"),
                ColorPickerMenu.parseButtonContentId("custompack:paint/icon"));
    }

    @Test public void customRgbIsParsedPrecisely() {
        assertEquals(0x00AABB, ColorPickerSettings.parseHex("#00aAbB"));
        assertEquals("#00AABB", ColorPickerSettings.formatHex(0x00AABB));
        for (String invalid : new String[]{"#abc", "red", "#GG00AA", "#FFFFFF00"}) {
            try { ColorPickerSettings.parseHex(invalid); fail("Accepted " + invalid); }
            catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void overlappingPaletteSlotIsRejected() {
        YamlConfiguration config = defaults();
        config.set("palettes.red.slot", 19);
        try { ColorPickerSettings.from(config); fail("Accepted overlapping input and red palette slots"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("overlaps")); }
    }

    @Test public void rejectsOutOfBoundsShadeSlot() {
        YamlConfiguration config = defaults();
        config.set("shade-slots", java.util.Arrays.asList(36,37,38,39,40,41,42,43,54));
        try { ColorPickerSettings.from(config); fail("Accepted out-of-range slot"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("out-of-range")); }
    }
}
