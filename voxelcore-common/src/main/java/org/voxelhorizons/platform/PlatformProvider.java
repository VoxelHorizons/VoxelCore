package org.voxelhorizons.platform;

import org.bukkit.plugin.Plugin;

public interface PlatformProvider {
    String name();
    boolean supports(Version version);
    VersionAdapter create(Version version, Plugin plugin);
}
