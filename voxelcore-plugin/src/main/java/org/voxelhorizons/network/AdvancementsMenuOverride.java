package org.voxelhorizons.network;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.platform.network.PacketChannelAdapter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class AdvancementsMenuOverride implements Listener {
    private static final String ADVANCEMENT_PACKET = "ServerboundSeenAdvancementsPacket";

    private final VoxelCore plugin;
    private final PacketChannelAdapter packets;

    private boolean enabled;
    private boolean debug;
    private String command;

    public AdvancementsMenuOverride(VoxelCore plugin, PacketChannelAdapter packets) {
        this.plugin = plugin;
        this.packets = packets;
        reload();
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (!packets.supported()) {
            if (enabled) {
                plugin.getLogger().warning("Advancements menu override is enabled but raw packet interception is unsupported on this platform.");
            }
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            inject(player);
        }

        if (enabled) {
            plugin.getLogger().info("Advancements menu override enabled; /" + command
                    + " will be executed when an OPENED_TAB packet is received.");
        }
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("menu_overrides.advancements.enabled", false);
        debug = plugin.getConfig().getBoolean("menu_overrides.advancements.debug", false);
        command = normalizeCommand(plugin.getConfig().getString("menu_overrides.advancements.command", "profile"));

        if (!packets.supported()) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            packets.uninject(player);
            inject(player);
        }
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        if (!packets.supported()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            packets.uninject(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Delay one tick so the player's play-state connection is fully installed.
        Bukkit.getScheduler().runTask(plugin, () -> inject(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        packets.uninject(event.getPlayer());
    }

    private void inject(Player player) {
        if (!packets.supported()) return;
        packets.inject(player, this::handleInboundPacket);
    }

    private boolean handleInboundPacket(Player player, Object packet) {
        if (packet == null || player == null) return false;

        String packetName = packet.getClass().getSimpleName();
        if (debug && packetName.toLowerCase(Locale.ROOT).contains("advancement")) {
            plugin.getLogger().info("[PacketDebug] " + player.getName() + " -> "
                    + packet.getClass().getName() + " action=" + readAction(packet));
        }

        if (!enabled || !ADVANCEMENT_PACKET.equals(packetName)) return false;

        String action = readAction(packet);
        if (!"OPENED_TAB".equalsIgnoreCase(action)) return false;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) player.performCommand(command);
        });

        // Consume the advancement-tab packet so vanilla server handling does not proceed.
        return true;
    }

    private static String readAction(Object packet) {
        Class<?> type = packet.getClass();

        for (Method method : type.getDeclaredMethods()) {
            if (method.getParameterCount() != 0 || !method.getReturnType().isEnum()) continue;
            try {
                method.setAccessible(true);
                Object value = method.invoke(packet);
                if (value instanceof Enum) return ((Enum<?>) value).name();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }

        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.getType().isEnum()) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(packet);
                    if (value instanceof Enum) return ((Enum<?>) value).name();
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
            type = type.getSuperclass();
        }

        return "UNKNOWN";
    }

    private static String normalizeCommand(String configured) {
        if (configured == null) return "profile";
        String command = configured.trim();
        while (command.startsWith("/")) command = command.substring(1);
        return command.isEmpty() ? "profile" : command;
    }
}
