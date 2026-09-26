package org.voxelhorizons.platform;

import org.voxelhorizons.platform.item.ItemPlatformAdapter;
import org.voxelhorizons.platform.network.PacketChannelAdapter;
import org.voxelhorizons.platform.network.UnsupportedPacketChannelAdapter;

public interface VersionAdapter {
    Version version();
    Capabilities capabilities();
    ItemPlatformAdapter items();

    default PacketChannelAdapter packets() {
        return new UnsupportedPacketChannelAdapter();
    }
}
