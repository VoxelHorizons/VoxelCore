package org.voxelhorizons.platform.v1_14;

import org.bukkit.plugin.Plugin;
import org.voxelhorizons.platform.PlatformProvider;
import org.voxelhorizons.platform.Version;
import org.voxelhorizons.platform.VersionAdapter;

public final class v1_14_PlatformProvider implements PlatformProvider {
    @Override
    public String name() {
        return "v1_14 (1.14-1.19.3)";
    }

    @Override
    public boolean supports(Version version) {
        return version.atLeast(1, 14, 0) && version.before(1, 19, 4);
    }

    @Override
    public VersionAdapter create(Version version, Plugin plugin) {
        return new v1_14_VersionAdapter(version, plugin);
    }
}
