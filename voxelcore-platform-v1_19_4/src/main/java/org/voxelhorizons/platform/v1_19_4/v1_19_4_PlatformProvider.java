package org.voxelhorizons.platform.v1_19_4;

import org.bukkit.plugin.Plugin;
import org.voxelhorizons.platform.PlatformProvider;
import org.voxelhorizons.platform.Version;
import org.voxelhorizons.platform.VersionAdapter;

public final class v1_19_4_PlatformProvider implements PlatformProvider {
    @Override
    public String name() { return "v1_19_4 (1.19.4-1.20.4)"; }

    @Override
    public boolean supports(Version version) {
        return version.atLeast(1, 19, 4) && version.before(1, 20, 5);
    }

    @Override
    public VersionAdapter create(Version version, Plugin plugin) {
        return new v1_19_4_VersionAdapter(version, plugin);
    }
}
