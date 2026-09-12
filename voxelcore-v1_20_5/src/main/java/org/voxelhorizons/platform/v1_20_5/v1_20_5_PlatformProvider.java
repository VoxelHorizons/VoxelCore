package org.voxelhorizons.platform.v1_20_5;

import org.bukkit.plugin.Plugin;
import org.voxelhorizons.platform.PlatformProvider;
import org.voxelhorizons.platform.Version;
import org.voxelhorizons.platform.VersionAdapter;

public final class v1_20_5_PlatformProvider implements PlatformProvider {
    @Override
    public String name() { return "v1_20_5 (1.20.5-1.21.3)"; }

    @Override
    public boolean supports(Version version) {
        return version.atLeast(1, 20, 5) && version.before(1, 21, 4);
    }

    @Override
    public VersionAdapter create(Version version, Plugin plugin) {
        return new v1_20_5_VersionAdapter(version, plugin);
    }
}
