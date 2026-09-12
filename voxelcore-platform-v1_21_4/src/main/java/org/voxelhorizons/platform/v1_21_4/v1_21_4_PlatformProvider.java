package org.voxelhorizons.platform.v1_21_4;

import org.bukkit.plugin.Plugin;
import org.voxelhorizons.platform.PlatformProvider;
import org.voxelhorizons.platform.Version;
import org.voxelhorizons.platform.VersionAdapter;

public final class v1_21_4_PlatformProvider implements PlatformProvider {
    @Override
    public String name() { return "v1_21_4 (1.21.4+)"; }

    @Override
    public boolean supports(Version version) {
        return version.atLeast(1, 21, 4);
    }

    @Override
    public VersionAdapter create(Version version, Plugin plugin) {
        return new v1_21_4_VersionAdapter(version, plugin);
    }
}
