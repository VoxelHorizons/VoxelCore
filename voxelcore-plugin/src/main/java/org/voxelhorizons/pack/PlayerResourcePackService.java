package org.voxelhorizons.pack;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.voxelhorizons.VoxelCore;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Applies the configured join/build pack policy and handles refusal/download failures. */
public final class PlayerResourcePackService implements Listener {
    private final JavaPlugin plugin;
    private final ResourcePackHost host;
    private final Set<UUID> pendingRequired = Collections.synchronizedSet(new HashSet<UUID>());

    public PlayerResourcePackService(JavaPlugin plugin, ResourcePackHost host) {
        this.plugin = plugin;
        this.host = host;
    }

    public void sendOnJoin(Player player) {
        if (plugin.getConfig().getBoolean("resource_pack.players.send_on_join", false)) send(player);
    }

    public void sendUpdatedPackToOnlinePlayers() {
        if (!plugin.getConfig().getBoolean("resource_pack.players.send_on_build", true)) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) send(player);
    }

    public boolean send(Player player) {
        if (player == null) return false;
        if (!host.isRunning()) {
            plugin.getLogger().warning("Resource pack not sent to " + player.getName() + ": HTTP host is not running. "
                    + "Check resource_pack.host.enabled, bind address/port, startup errors, and container port allocation.");
            return false;
        }
        if (host.sha1() == null) {
            plugin.getLogger().warning("Resource pack not sent to " + player.getName() + ": no hosted pack file is loaded. "
                    + "Run /vc admin pack build for the running server target.");
            return false;
        }
        String url = configuredPackUrl();
        if (url == null) {
            plugin.getLogger().warning("Resource pack was not sent: set resource_pack.host.public_url or public_host to an address clients can reach.");
            return false;
        }
        boolean required = plugin.getConfig().getBoolean("resource_pack.players.required", false);
        String prompt = color(plugin.getConfig().getString("resource_pack.players.prompt", ""));
        if (required) pendingRequired.add(player.getUniqueId());
        try {
            plugin.getLogger().info("Sending resource pack to " + player.getName() + ": url=" + url
                    + " required=" + required + " sha1=" + host.sha1Hex());
            invokeCompatibleSend(player, url, host.sha1(), required, prompt);
            return true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            pendingRequired.remove(player.getUniqueId());
            plugin.getLogger().log(Level.WARNING, "Unable to send the VoxelCore resource pack to " + player.getName(), failure);
            if (required) player.kickPlayer(color(plugin.getConfig().getString("resource_pack.players.failure_kick_message",
                    "You must accept and load the server resource pack to play.")));
            return false;
        }
    }

    public void reloadHost() {
        boolean enabled = plugin.getConfig().getBoolean("resource_pack.host.enabled", false);
        host.setRequestDebug(plugin.getConfig().getBoolean("resource_pack.host.debug", false));
        if (!enabled) {
            host.stop();
            plugin.getLogger().info("Self-hosted resource-pack HTTP server is disabled (resource_pack.host.enabled=false).");
            return;
        }
        String bindAddress = plugin.getConfig().getString("resource_pack.host.bind_address", "0.0.0.0");
        int configuredPort = plugin.getConfig().getInt("resource_pack.host.port", 24464);
        try {
            host.start(bindAddress, configuredPort);
            if (host.sha1() == null && !host.loadExisting() && plugin instanceof VoxelCore) {
                VoxelCore voxelCore = (VoxelCore) plugin;
                JavaPackTarget target = voxelCore.getPackManager().currentTarget();
                if (target != null) {
                    java.nio.file.Path builtPack = voxelCore.getPackManager().outputRoot().resolve(target.id() + ".zip");
                    if (java.nio.file.Files.isRegularFile(builtPack)) host.publish(builtPack);
                }
            }
            String url = configuredPackUrl();
            long fileSize = host.hostedPackSize();
            plugin.getLogger().info("Resource-pack HTTP host started: bind=" + bindAddress + ":" + host.port()
                    + " configuredPort=" + configuredPort + " endpoint=" + ResourcePackHost.RESOURCE_PATH
                    + " advertisedUrl=" + (url == null ? "not configured" : url)
                    + " packFile=" + host.hostedPack() + " packBytes=" + (fileSize < 0 ? "missing" : fileSize)
                    + " sha1=" + host.sha1Hex() + " requestDebug="
                    + plugin.getConfig().getBoolean("resource_pack.host.debug", false));
            if (url == null) plugin.getLogger().warning("Resource-pack host is listening but no advertised URL can be formed. "
                    + "Set resource_pack.host.public_host or resource_pack.host.public_url.");
            plugin.getLogger().info("If this server runs in Docker/Pterodactyl, expose the container port "
                    + host.port() + " on the host before testing its LAN address.");
        } catch (Exception failure) {
            host.stop();
            plugin.getLogger().log(Level.SEVERE, "Could not start the resource-pack HTTP host at "
                    + bindAddress + ":" + configuredPort + ". Check for another listener, a valid bind address, "
                    + "and permission to bind this port.", failure);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("resource_pack.players.send_on_join", false)) return;
        // Ensure the join screen/player connection has completed before issuing the pack request.
        plugin.getServer().getScheduler().runTask(plugin, () -> send(event.getPlayer()));
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!pendingRequired.contains(id)) return;
        String status = event.getStatus().name();
        if ("SUCCESSFULLY_LOADED".equals(status)) {
            pendingRequired.remove(id);
            return;
        }
        if ("DECLINED".equals(status) || "FAILED_DOWNLOAD".equals(status)
                || "INVALID_URL".equals(status) || "FAILED_RELOAD".equals(status)) {
            pendingRequired.remove(id);
            String message = plugin.getConfig().getString("resource_pack.players.failure_kick_message",
                    "You must accept and load the server resource pack to play.");
            event.getPlayer().kickPlayer(color(message));
        }
    }

    public String configuredPackUrl() {
        String configured = plugin.getConfig().getString("resource_pack.host.public_url", "").trim();
        if (!configured.isEmpty()) {
            String normalized = configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
            return normalized.endsWith(ResourcePackHost.RESOURCE_PATH)
                    ? normalized : normalized + ResourcePackHost.RESOURCE_PATH;
        }
        String publicHost = plugin.getConfig().getString("resource_pack.host.public_host", "").trim();
        if (publicHost.isEmpty()) publicHost = plugin.getServer().getIp().trim();
        if (publicHost.isEmpty() || "0.0.0.0".equals(publicHost) || "::".equals(publicHost)) return null;
        String scheme = plugin.getConfig().getString("resource_pack.host.protocol", "http").trim();
        int port = plugin.getConfig().getInt("resource_pack.host.port", 24464);
        try {
            URI uri = new URI(scheme + "://" + (publicHost.contains(":") && !publicHost.startsWith("[")
                    ? "[" + publicHost + "]" : publicHost) + ":" + port + ResourcePackHost.RESOURCE_PATH);
            return uri.toASCIIString();
        } catch (URISyntaxException invalid) {
            plugin.getLogger().warning("Invalid resource_pack.host.public_host: " + publicHost);
            return null;
        }
    }

    private static void invokeCompatibleSend(Player player, String url, byte[] hash, boolean required, String prompt)
            throws ReflectiveOperationException {
        Method[] methods = player.getClass().getMethods();
        // Prefer the modern API so the client can verify the SHA-1 and display a custom prompt.
        for (Method method : methods) {
            Class<?>[] types = method.getParameterTypes();
            if (!"setResourcePack".equals(method.getName()) || types.length != 4
                    || types[0] != String.class || types[1] != byte[].class || types[2] != boolean.class) continue;
            Object promptValue = promptValue(types[3], prompt);
            if (promptValue != null) {
                method.invoke(player, url, hash, required, promptValue);
                return;
            }
        }
        for (Method method : methods) {
            Class<?>[] types = method.getParameterTypes();
            if ("setResourcePack".equals(method.getName()) && types.length == 3
                    && types[0] == String.class && types[1] == byte[].class && types[2] == boolean.class) {
                method.invoke(player, url, hash, required);
                return;
            }
        }
        for (Method method : methods) {
            Class<?>[] types = method.getParameterTypes();
            if ("setResourcePack".equals(method.getName()) && types.length == 2
                    && types[0] == String.class && types[1] == byte[].class) {
                method.invoke(player, url, hash);
                return;
            }
        }
        player.setResourcePack(url);
    }

    private static Object promptValue(Class<?> type, String prompt) {
        if (type == String.class) return prompt;
        try {
            Method text = type.getMethod("text", String.class);
            return text.invoke(null, prompt);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }
}
