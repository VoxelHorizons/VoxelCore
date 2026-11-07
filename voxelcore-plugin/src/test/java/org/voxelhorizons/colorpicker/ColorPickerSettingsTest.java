package org.voxelhorizons.colorpicker;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    @Test public void bundledPrimaryHexesStayIndependentFromLightToDarkShades() {
        ColorPickerSettings settings = ColorPickerSettings.from(defaults());
        assertTrue(settings.enabled);
        assertEquals(54, settings.size);
        assertEquals(19, settings.inputSlot);
        assertEquals(25, settings.outputSlot);
        assertEquals("", settings.selectedShadeMaterial);
        assertEquals("&f:offset_-8::colorpicker_background:", settings.title);
        assertEquals(9, settings.palettes.size());
        assertEquals(9, settings.shadeSlots.size());
        // Keep every previous primary-grid colour unchanged while explicitly
        // configuring it independently of the new lightest-first shade.
        int[][] expected = {
            {0x991E1E, 0xBF9999, 0x3A2525},
            {0xCC6A28, 0xFFE0CC, 0x4E3D31},
            {0xCCB028, 0xFFF6CC, 0x4E4931},
            {0xCC28B6, 0xFFCCF8, 0x4E314A},
            {0xCCCCCC, 0xECE8DC, 0x28282A},
            {0x59CC28, 0xDBFFCC, 0x3A4E31},
            {0x9828CC, 0xEFCCFF, 0x561C66},
            {0x2841CC, 0xCCD4FF, 0x1C2066},
            {0x28CCCC, 0xCCFFFF, 0x314E4E}
        };
        int[] slots = {12, 13, 14, 21, 22, 30, 23, 32, 31};
        for (int i = 0; i < expected.length; i++) {
            ColorPickerSettings.Palette p = settings.palettes.get(i);
            assertEquals(9, p.shades.size());
            assertEquals(slots[i], p.slot);
            assertEquals(expected[i][0], p.buttonColor);
            assertEquals(expected[i][1], p.shades.get(0).intValue());
            assertEquals(expected[i][2], p.shades.get(8).intValue());
            // Verify perceptual lightness, not the numeric hex value.
            for (int j = 1; j < p.shades.size(); j++)
                assertTrue(p.id + " shade " + j + " is brighter than " + (j - 1),
                        relativeLuminance(p.shades.get(j - 1).intValue())
                                >= relativeLuminance(p.shades.get(j).intValue()));
        }
    }

    private static double relativeLuminance(int rgb) {
        return 0.2126 * linear((rgb >>> 16) & 255)
                + 0.7152 * linear((rgb >>> 8) & 255)
                + 0.0722 * linear(rgb & 255);
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    @Test public void primaryButtonColorOverridesFirstShadeWithoutChangingShades() {
        YamlConfiguration config = defaults();
        config.set("palettes.red.button-color", "#1234AB");
        ColorPickerSettings settings = ColorPickerSettings.from(config);
        assertEquals(0x1234AB, settings.palettes.get(0).buttonColor);
        assertEquals(0xBF9999, settings.palettes.get(0).shades.get(0).intValue());
    }

    @Test public void oldConfigsWithoutButtonHexStillUseTheirFirstShade() {
        YamlConfiguration config = defaults();
        config.set("palettes.red.button-color", null);
        ColorPickerSettings settings = ColorPickerSettings.from(config);
        assertEquals(0xBF9999, settings.palettes.get(0).buttonColor);
        assertEquals(settings.palettes.get(0).shades.get(0).intValue(),
                settings.palettes.get(0).buttonColor);
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

    @Test public void nestedResourceIconPathsMatchBundledContentItemIds() {
        assertEquals(org.voxelhorizons.content.ContentID.of("voxel", "ui-colorpicker_slot"),
                ColorPickerMenu.parseButtonContentId("voxel:ui/colorpicker/slot"));
        assertEquals(org.voxelhorizons.content.ContentID.of("voxel", "ui-colorpicker_selected"),
                ColorPickerMenu.parseButtonContentId("voxel:ui/colorpicker/selected"));
        assertEquals(org.voxelhorizons.content.ContentID.of("voxel", "ui-colorpicker_slot"),
                ColorPickerMenu.parseButtonContentId("voxel:ui-colorpicker_slot"));
        assertEquals(java.util.Arrays.asList(
                        org.voxelhorizons.content.ContentID.of("voxel", "ui-colorpicker_slot"),
                        org.voxelhorizons.content.ContentID.of("voxel", "ui-colorpicker-slot")),
                ColorPickerMenu.buttonContentCandidates("voxel:ui/colorpicker/slot"));
    }

    @Test public void selectedPrimaryAndShadeUseSelectedModelWithFallback() {
        String normal = "voxel:ui-colorpicker_slot";
        String selected = "voxel:ui-colorpicker_selected";
        assertEquals(selected, ColorPickerMenu.selectionIcon(true, normal, selected));
        assertEquals(normal, ColorPickerMenu.selectionIcon(false, normal, selected));
        assertEquals(normal, ColorPickerMenu.selectionIcon(true, normal, ""));
    }

    @Test public void completedOutputAcceptsOnlyNormalOrShiftClicks() {
        assertTrue(ColorPickerMenu.shiftOutputClick(org.bukkit.event.inventory.ClickType.SHIFT_LEFT));
        assertTrue(ColorPickerMenu.shiftOutputClick(org.bukkit.event.inventory.ClickType.SHIFT_RIGHT));
        assertFalse(ColorPickerMenu.shiftOutputClick(org.bukkit.event.inventory.ClickType.LEFT));
        assertFalse(ColorPickerMenu.shiftOutputClick(org.bukkit.event.inventory.ClickType.RIGHT));
        assertFalse(ColorPickerMenu.shiftOutputClick(org.bukkit.event.inventory.ClickType.DOUBLE_CLICK));
        assertFalse(ColorPickerMenu.shiftOutputClick(org.bukkit.event.inventory.ClickType.NUMBER_KEY));
        assertFalse(ColorPickerMenu.shiftOutputClick(org.bukkit.event.inventory.ClickType.DROP));
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
