package org.voxelhorizons.platform.server;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ServerPlatformCapabilitiesFactoryTest {
    @Test public void detectsPaperFromServerName() {
        ServerPlatformCapabilities capabilities = ServerPlatformCapabilitiesFactory.create(
                "Paper", unavailableClasses());
        assertTrue(capabilities.paperApiAvailable());
        assertEquals("Paper", capabilities.platformName());
    }

    @Test public void detectsPaperFromModernApiMarker() {
        ServerPlatformCapabilities capabilities = ServerPlatformCapabilitiesFactory.create(
                "CraftBukkit", availableClass("io.papermc.paper.ServerBuildInfo"));
        assertTrue(capabilities.paperApiAvailable());
    }

    @Test public void detectsPaperFromLegacyApiMarker() {
        ServerPlatformCapabilities capabilities = ServerPlatformCapabilitiesFactory.create(
                "CraftBukkit", availableClass("com.destroystokyo.paper.PaperConfig"));
        assertTrue(capabilities.paperApiAvailable());
    }

    @Test public void fallsBackToBukkitCompatibleCapabilities() {
        ServerPlatformCapabilities capabilities = ServerPlatformCapabilitiesFactory.create(
                "Spigot", unavailableClasses());
        assertFalse(capabilities.paperApiAvailable());
        assertEquals("Bukkit-compatible", capabilities.platformName());
    }

    private static ServerPlatformCapabilitiesFactory.ClassAvailability unavailableClasses() {
        return new ServerPlatformCapabilitiesFactory.ClassAvailability() {
            @Override public boolean isAvailable(String className) { return false; }
        };
    }

    private static ServerPlatformCapabilitiesFactory.ClassAvailability availableClass(final String expected) {
        return new ServerPlatformCapabilitiesFactory.ClassAvailability() {
            @Override public boolean isAvailable(String className) { return expected.equals(className); }
        };
    }
}
