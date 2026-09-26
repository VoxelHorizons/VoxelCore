package org.voxelhorizons.platform.network;

import org.bukkit.entity.Player;

public final class UnsupportedPacketChannelAdapter implements PacketChannelAdapter {
    @Override
    public boolean supported() { return false; }

    @Override
    public void inject(Player player, PacketInterceptor interceptor) {
        // Unsupported on this platform/version.
    }

    @Override
    public void uninject(Player player) {
        // Unsupported on this platform/version.
    }
}
