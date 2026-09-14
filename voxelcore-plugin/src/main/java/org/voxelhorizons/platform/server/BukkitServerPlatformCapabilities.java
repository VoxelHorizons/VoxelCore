package org.voxelhorizons.platform.server;

final class BukkitServerPlatformCapabilities implements ServerPlatformCapabilities {
    @Override public String platformName() { return "Bukkit-compatible"; }
    @Override public boolean paperApiAvailable() { return false; }
}
