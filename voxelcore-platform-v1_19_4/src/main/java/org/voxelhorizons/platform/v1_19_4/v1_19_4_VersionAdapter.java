package org.voxelhorizons.platform.v1_19_4;

import org.bukkit.plugin.Plugin;
import org.voxelhorizons.platform.Capabilities;
import org.voxelhorizons.platform.Version;
import org.voxelhorizons.platform.VersionAdapter;
import org.voxelhorizons.platform.item.ItemPlatformAdapter;

public final class v1_19_4_VersionAdapter implements VersionAdapter {
    private final Version version;
    private final ItemPlatformAdapter itemAdapter;

    public v1_19_4_VersionAdapter(Version version, Plugin plugin) {
        this.version = version;
        this.itemAdapter = new v1_19_4_ItemAdapter(plugin);
    }

    @Override
    public Version version() { return version; }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(true, true, true, false, false, false);
    }

    @Override
    public ItemPlatformAdapter items() { return itemAdapter; }
}
