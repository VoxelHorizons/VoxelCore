package org.voxelhorizons.platform.v26_3;

import org.bukkit.plugin.Plugin;
import org.voxelhorizons.platform.PlatformProvider;
import org.voxelhorizons.platform.Version;
import org.voxelhorizons.platform.VersionAdapter;

public final class v26_3_PlatformProvider implements PlatformProvider {
    @Override
    public String name() { return "v26_3 (26.3 validated)"; }

    @Override
    public boolean supports(Version version) {
        return version.compareTo(Version.of(26, 3, 0)) == 0;
    }

    @Override
    public VersionAdapter create(Version version, Plugin plugin) {
        return new v26_3_VersionAdapter(version, plugin);
    }
}
