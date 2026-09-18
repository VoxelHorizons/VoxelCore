package org.voxelhorizons.integration.shopgui;

import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.content.ContentID;

import java.util.Optional;

interface VoxelCoreItemResolver {
    ItemStack create(ContentID id);

    Optional<ContentID> identify(ItemStack stack);
}
