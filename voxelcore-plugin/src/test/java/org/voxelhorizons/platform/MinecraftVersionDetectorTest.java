package org.voxelhorizons.platform;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MinecraftVersionDetectorTest {
    @Test
    public void prefersTheGameVersionWhenBukkitApiVersionDiffers() {
        assertEquals("26.3", MinecraftVersionDetector.detect(
                "26.3", "Paper version 26.3 (MC: 26.3)", "26.2.build.209-stable"));
    }

    @Test
    public void usesMinecraftMarkerBeforeBukkitApiFallback() {
        assertEquals("26.3", MinecraftVersionDetector.detect(
                null, "Paper version 26.3 (MC: 26.3)", "26.2.build.209-stable"));
    }

    @Test
    public void fallsBackToLegacyBukkitVersionWhenNoGameMarkerExists() {
        assertEquals("1.12.2", MinecraftVersionDetector.detect(
                null, "git-Paper (unknown)", "1.12.2-R0.1-SNAPSHOT"));
    }

    @Test
    public void failsClearlyWhenNoVersionSourceCanBeParsed() {
        try {
            MinecraftVersionDetector.detect(null, "unknown server", "unknown API");
            fail("Expected version detection to fail");
        } catch (IllegalStateException expected) {
            assertEquals("Unable to determine the Minecraft game version from the server APIs", expected.getMessage());
        }
    }
}
