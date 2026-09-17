package org.voxelhorizons.text;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Resolves VoxelCore placeholders on the Bukkit player-list surfaces.
 *
 * <p>Tab-list plugins commonly update these values on a repeating task and Bukkit has no event for
 * those writes. Running after each server tick gives those plugins a stable integration without a
 * hard dependency. Reflection keeps the plugin compatible with the 1.12 API, which lacks the
 * header/footer accessors present on newer servers.</p>
 */
public final class PlayerListPlaceholderSynchronizer implements Runnable {
    private final Plugin plugin;
    private final TextPlaceholderService placeholders;
    private final Method getHeader;
    private final Method getFooter;
    private final Method setHeaderFooter;
    private boolean warnedFailure;

    public PlayerListPlaceholderSynchronizer(Plugin plugin, TextPlaceholderService placeholders) {
        if (plugin == null) throw new IllegalArgumentException("plugin cannot be null");
        if (placeholders == null) throw new IllegalArgumentException("placeholders cannot be null");
        this.plugin = plugin;
        this.placeholders = placeholders;
        this.getHeader = method("getPlayerListHeader");
        this.getFooter = method("getPlayerListFooter");
        this.setHeaderFooter = method("setPlayerListHeaderFooter", String.class, String.class);
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 1L, 1L);
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            resolveDisplayName(player);
            resolveHeaderFooter(player);
        }
    }

    private void resolveDisplayName(Player player) {
        String current = player.getPlayerListName();
        String resolved = placeholders.resolve(current);
        if (changed(current, resolved)) player.setPlayerListName(resolved);
    }

    private void resolveHeaderFooter(Player player) {
        if (getHeader == null || getFooter == null || setHeaderFooter == null) return;
        try {
            String header = (String) getHeader.invoke(player);
            String footer = (String) getFooter.invoke(player);
            String resolvedHeader = placeholders.resolve(header);
            String resolvedFooter = placeholders.resolve(footer);
            if (changed(header, resolvedHeader) || changed(footer, resolvedFooter)) {
                setHeaderFooter.invoke(player, resolvedHeader, resolvedFooter);
            }
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException exception) {
            warnFailureOnce(exception);
        }
    }

    static boolean changed(String before, String after) {
        return before != null && after != null && !before.equals(after);
    }

    private static Method method(String name, Class<?>... parameters) {
        try {
            return Player.class.getMethod(name, parameters);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private void warnFailureOnce(Exception exception) {
        if (warnedFailure) return;
        warnedFailure = true;
        plugin.getLogger().warning("Unable to apply player-list placeholders: " + exception.getMessage());
    }
}
