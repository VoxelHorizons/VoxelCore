package org.voxelhorizons.platform.item;

import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;

import java.util.Optional;

public interface ItemPlatformAdapter {
    ItemStack createItem(ItemDefinition definition, int quantity);
    Optional<ContentID> getContentId(ItemStack stack);
    void setContentId(ItemStack stack, ContentID id);
}
