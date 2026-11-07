package org.voxelhorizons.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class BedrockPaletteConfigTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void deduplicatesAllShadeAndPrimaryColoursInStableRgbOrder() throws Exception {
        Path file = temp.newFile("colorpicker.yml").toPath();
        Files.write(file, ("enabled: true\npalettes:\n"
                + "  red:\n    shades: ['#FF0000', '#AA0000', '#FF0000']\n"
                + "    button-color: '#00FF00'\n"
                + "  yellow:\n    shades: ['#0000FF', '#AA0000']\n")
                .getBytes(StandardCharsets.UTF_8));
        assertEquals(Arrays.asList(0x0000FF, 0x00FF00, 0xAA0000, 0xFF0000),
                BedrockPaletteConfig.read(file));
        assertEquals("voxelcore:rgb:00FF00", BedrockPaletteConfig.selector(0x00FF00));
    }

    @Test public void respectsDisabledColorPickerAndMissingConfig() throws Exception {
        Path file = temp.newFile("disabled.yml").toPath();
        Files.write(file, "enabled: false\npalettes: {}\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(Collections.emptyList(), BedrockPaletteConfig.read(file));
        assertEquals(Collections.emptyList(), BedrockPaletteConfig.read(file.resolveSibling("missing.yml")));
    }

    @Test public void rejectsInvalidRgbWithoutPublishingWrongIcons() throws Exception {
        Path file = temp.newFile("invalid.yml").toPath();
        Files.write(file, ("palettes:\n  red:\n    shades: ['#GGRRRR']\n")
                .getBytes(StandardCharsets.UTF_8));
        try {
            BedrockPaletteConfig.read(file);
            fail("Invalid palette must not silently produce the default colours");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("Invalid RGB"));
        }
    }

    @Test public void doesNotAllowMoreThanNineShadesPerPalette() throws Exception {
        Path file = temp.newFile("too-many.yml").toPath();
        Files.write(file, ("palettes:\n  red:\n"
                + "    shades: ['#000000', '#111111', '#222222', '#333333', '#444444',"
                + " '#555555', '#666666', '#777777', '#888888', '#999999']\n")
                .getBytes(StandardCharsets.UTF_8));
        try {
            BedrockPaletteConfig.read(file);
            fail("Oversized palettes must be rejected");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("1..9"));
        }
    }
}
