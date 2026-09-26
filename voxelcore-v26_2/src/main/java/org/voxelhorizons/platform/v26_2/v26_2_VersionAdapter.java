package org.voxelhorizons.platform.v26_2;

import org.bukkit.plugin.Plugin;
import org.voxelhorizons.platform.Capabilities;
import org.voxelhorizons.platform.Version;
import org.voxelhorizons.platform.VersionAdapter;
import org.voxelhorizons.platform.item.ItemPlatformAdapter;
import org.voxelhorizons.platform.network.PacketChannelAdapter;

public final class v26_2_VersionAdapter implements VersionAdapter {
    private final Version version;
    private final ItemPlatformAdapter itemAdapter;
    private final PacketChannelAdapter packetAdapter;

    public v26_2_VersionAdapter(Version version, Plugin plugin) {
        this.version = version;
        this.itemAdapter = new v26_2_ItemAdapter(plugin);
        this.packetAdapter = new v26_2_PacketChannelAdapter();
    }

    @Override
    public Version version() { return version; }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(true, true, true, true, true, true);
    }

    @Override
    public ItemPlatformAdapter items() { return itemAdapter; }

    @Override
    public PacketChannelAdapter packets() { return packetAdapter; }
}
