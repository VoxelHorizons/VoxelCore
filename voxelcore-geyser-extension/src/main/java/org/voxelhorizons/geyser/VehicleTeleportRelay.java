package org.voxelhorizons.geyser;

import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Geyser answers an absolute teleport of a non-living vehicle ridden by the Bedrock player
 * with a ServerboundMoveVehiclePacket, as a Java client does for a vehicle it controls. A
 * VoxelCore vehicle is an item_display nobody controls, so Paper logged "... was expected to
 * be controlling vehicle ... but was not. Resetting vehicle position." on every move (live test).
 * The server only teleports it; nothing needs echoing.
 *
 * For those vehicles the teleport is handed to Geyser as the equivalent relative move. Geyser
 * then interpolates the movement (as for living entities) instead of snapping, and sends nothing
 * back. The result on the client is the same position. Other entities pass through untouched.
 *
 * Uses Geyser internals like {@link ItemDisplayRegistration}; callers isolate LinkageErrors.
 */
final class VehicleTeleportRelay {
    private VehicleTeleportRelay() {}

    private static final List<PositionElement> XYZ = List.of(PositionElement.X, PositionElement.Y, PositionElement.Z);

    @SuppressWarnings("unchecked")
    static void install() {
        PacketTranslator<ClientboundTeleportEntityPacket> original = (PacketTranslator<ClientboundTeleportEntityPacket>)
                Registries.JAVA_PACKET_TRANSLATORS.get(ClientboundTeleportEntityPacket.class);
        if (original == null) throw new IllegalStateException("Geyser has no teleport-entity translator");
        Registries.JAVA_PACKET_TRANSLATORS.register(ClientboundTeleportEntityPacket.class,
                new PacketTranslator<ClientboundTeleportEntityPacket>() {
                    @Override
                    public void translate(GeyserSession session, ClientboundTeleportEntityPacket packet) {
                        original.translate(session, relayed(session, packet));
                    }

                    @Override
                    public boolean shouldExecuteInEventLoop() {
                        return original.shouldExecuteInEventLoop();
                    }
                });
    }

    /** The packet, or for a ridden VoxelCore display vehicle the same move made relative. */
    static ClientboundTeleportEntityPacket relayed(GeyserSession session, ClientboundTeleportEntityPacket packet) {
        Entity entity = session.getEntityCache().getEntityByJavaId(packet.getId());
        // ItemDisplayRegistration builds item_display as the plain Entity class; vanilla
        // non-living vehicles (boats, minecarts) are subclasses and keep Geyser's behaviour.
        if (entity == null || entity.getClass() != Entity.class
                || !entity.getPassengers().contains(session.getPlayerEntity())) return packet;
        List<PositionElement> relatives = packet.getRelatives();
        if (relatives.contains(PositionElement.X) || relatives.contains(PositionElement.Y)
                || relatives.contains(PositionElement.Z)) return packet;
        Vector3d current = entity.position().toDouble();
        List<PositionElement> relative = new ArrayList<>(relatives);
        relative.addAll(XYZ);
        return packet.withPosition(packet.getPosition().sub(current)).withRelatives(relative);
    }
}
