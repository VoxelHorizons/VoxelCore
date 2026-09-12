package org.voxelhorizons.item;

import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.platform.VersionAdapter;

import java.util.Optional;

public final class ItemManager {

    private final ItemDefinitionRegistry registry;
    private final VersionAdapter platform;

    public ItemManager(
            ItemDefinitionRegistry registry,
            VersionAdapter platform
    ) {
        this.registry = registry;
        this.platform = platform;
    }

    public Optional<ItemDefinition> getDefinition(ContentID id) {
        return registry.get(id);
    }

    public boolean hasItem(ContentID id) {
        return registry.contains(id);
    }

    public ItemStack createItem(ContentID id) {
        return createItem(id, 1);
    }

    public ItemStack createItem(
            ContentID id,
            int quantity
    ) {
        ItemDefinition definition = registry.get(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unknown item: " + id
                        )
                );

        return platform.items()
                .createItem(definition, quantity);
    }

    public Optional<ContentID> identify(ItemStack stack) {
        return platform.items().getContentId(stack);
    }
}