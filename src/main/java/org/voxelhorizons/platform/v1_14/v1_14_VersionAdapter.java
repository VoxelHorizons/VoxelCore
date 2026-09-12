package org.voxelhorizons.platform.v1_14;

import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.platform.Capabilities;
import org.voxelhorizons.platform.Version;
import org.voxelhorizons.platform.VersionAdapter;
import org.voxelhorizons.platform.item.ItemPlatformAdapter;

public final class v1_14_VersionAdapter implements VersionAdapter {

    private final ItemPlatformAdapter itemAdapter;

    public v1_14_VersionAdapter(VoxelCore plugin) {
        this.itemAdapter = new v1_14_ItemAdapter(plugin);
    }

    @Override
    public Version version() {
        return new Version(1, 14, 0);
    }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(
                true,   // PDC
                true,   // Numeric custom model data
                false,  // Display entities
                false,  // Components
                false,  // Structured custom model data
                false   // Namespaced item models
        );
    }

    @Override
    public ItemPlatformAdapter items() {
        return itemAdapter;
    }
}