package org.voxelhorizons.platform;

import org.bukkit.Bukkit;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.platform.v1_14.v1_14_VersionAdapter;

public final class VersionAdapterFactory {

    private VersionAdapterFactory() {
    }

    public static VersionAdapter create(VoxelCore plugin) {

        String minecraftVersion =
                Bukkit.getBukkitVersion()
                        .split("-")[0];

        plugin.getLogger().info(
                "Detected Minecraft version: "
                        + minecraftVersion
        );

        Version version =
                Version.parse(minecraftVersion);

        if (version.atLeast(1, 14, 0)) {
            return new v1_14_VersionAdapter(plugin);
        }

        throw new UnsupportedOperationException(
                "Minecraft " + minecraftVersion
                        + " is not supported yet."
        );
    }
}