package org.voxelhorizons.integration.shopgui;

import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.content.ContentID;

import java.util.Optional;

interface VoxelCoreItemResolver {
    /** Creates an inventory/trade item. Abstract definitions must be rejected. */
    ItemStack create(ContentID id);

    /** Creates a display-only icon; renderable abstract definitions are permitted. */
    ItemStack createRender(ContentID id);

    Optional<ContentID> identify(ItemStack stack);
}
