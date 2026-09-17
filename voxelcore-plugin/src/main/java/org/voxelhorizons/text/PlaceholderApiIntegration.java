package org.voxelhorizons.text;

import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Loads the optional PlaceholderAPI expansion without linking it when PlaceholderAPI is absent. */
public final class PlaceholderApiIntegration {
    private static final String EXPANSION = "org.voxelhorizons.text.VoxelCorePlaceholderExpansion";

    private PlaceholderApiIntegration() {}

    public static boolean registerIfAvailable(Plugin plugin, TextPlaceholderService placeholders) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) return false;
        try {
            Class<?> type = Class.forName(EXPANSION, true, plugin.getClass().getClassLoader());
            Constructor<?> constructor = type.getConstructor(
                    org.voxelhorizons.VoxelCore.class, TextPlaceholderService.class);
            Object expansion = constructor.newInstance(plugin, placeholders);
            Method register = type.getMethod("register");
            Object result = register.invoke(expansion);
            if (result instanceof Boolean && !((Boolean) result).booleanValue()) {
                plugin.getLogger().warning("PlaceholderAPI rejected the VoxelCore expansion registration.");
                return false;
            }
            plugin.getLogger().info("Enabled PlaceholderAPI font placeholders (%voxelcore_font_<name>%).");
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Unable to register PlaceholderAPI integration: " + exception.getMessage());
            return false;
        }
    }
}
