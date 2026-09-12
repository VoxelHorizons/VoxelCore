package org.voxelhorizons.platform.item;

import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.content.render.RenderAllocation;

import java.util.Optional;

public interface ItemPlatformAdapter {
    ItemStack createItem(ItemDefinition definition, int quantity);

    default ItemStack createItem(ItemDefinition definition, int quantity, RenderAllocation allocation) {
        return createItem(definition, quantity);
    }

    Optional<ContentID> getContentId(ItemStack stack);
    ItemStack setContentId(ItemStack stack, ContentID id);
}
