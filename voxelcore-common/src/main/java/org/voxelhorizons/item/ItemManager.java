package org.voxelhorizons.item;

import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.content.render.RenderAllocation;
import org.voxelhorizons.content.runtime.ContentRuntime;
import org.voxelhorizons.content.runtime.ContentSnapshot;
import org.voxelhorizons.platform.VersionAdapter;

import java.util.Optional;

public final class ItemManager {

    private final ContentRuntime content;
    private final VersionAdapter platform;

    public ItemManager(ContentRuntime content, VersionAdapter platform) {
        this.content = content;
        this.platform = platform;
    }

    public Optional<ItemDefinition> getDefinition(ContentID id) {
        return content.current().items().get(id);
    }

    public boolean hasItem(ContentID id) {
        return content.current().items().contains(id);
    }

    public ItemStack createItem(ContentID id) {
        return createItem(id, 1);
    }

    public ItemStack createItem(ContentID id, int quantity) {
        ContentSnapshot snapshot = content.current();
        ItemDefinition definition = snapshot.items().get(id).orElseThrow(() ->
                new IllegalArgumentException("Unknown item: " + id)
        );
        RenderAllocation allocation = snapshot.renderAllocations().get(id).orElse(null);
        return platform.items().createItem(definition, quantity, allocation);
    }

    public Optional<ContentID> identify(ItemStack stack) {
        return platform.items().getContentId(stack);
    }
}
