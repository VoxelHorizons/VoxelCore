package org.voxelhorizons.platform.network;

import org.bukkit.entity.Player;

public interface PacketChannelAdapter {
    boolean supported();
    void inject(Player player, PacketInterceptor interceptor);
    void uninject(Player player);

    /**
     * Requests that the client immediately close its current screen.
     * Implementations should fail closed when unsupported.
     */
    default boolean closeClientScreen(Player player) { return false; }

    /**
     * Temporarily clears the client's advancement cache without changing server-side progress.
     */
    default boolean blankClientAdvancements(Player player) { return false; }

    /**
     * Restores the client's advancement cache from the server's current in-memory advancement state.
     */
    default boolean restoreClientAdvancements(Player player) { return false; }

    interface PacketInterceptor {
        /**
         * @return true to consume/cancel the inbound packet, false to pass it through.
         */
        boolean onInboundPacket(Player player, Object packet);
    }
}
