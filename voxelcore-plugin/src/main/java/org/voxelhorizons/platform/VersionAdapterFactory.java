package org.voxelhorizons.platform;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.ServiceLoader;

public final class VersionAdapterFactory {

    private VersionAdapterFactory() {
    }

    public static VersionAdapter create(Plugin plugin) {
        String minecraftVersion = MinecraftVersionDetector.detect();
        Version version = Version.parse(minecraftVersion);

        plugin.getLogger().info("Detected Minecraft game version: " + minecraftVersion);

        ServiceLoader<PlatformProvider> providers = ServiceLoader.load(
                PlatformProvider.class,
                plugin.getClass().getClassLoader()
        );

        for (PlatformProvider provider : providers) {
            if (provider.supports(version)) {
                plugin.getLogger().info("Using VoxelCore platform adapter: " + provider.name());
                return provider.create(version, plugin);
            }
        }

        throw new UnsupportedOperationException(
                "No VoxelCore platform adapter is available for Minecraft " + minecraftVersion
        );
    }
}
