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
        return create(id, quantity, false);
    }

    /** Creates an internal display-only model. Abstract definitions are never exposed through give commands. */
    public ItemStack createRenderItem(ContentID id) {
        return create(id, 1, true);
    }

    private ItemStack create(ContentID id, int quantity, boolean allowAbstract) {
        ContentSnapshot snapshot = content.current();
        ItemDefinition definition = snapshot.items().get(id).orElseThrow(() ->
                new IllegalArgumentException("Unknown item: " + id)
        );
        if (definition.abstractDefinition() && !allowAbstract) {
            throw new IllegalArgumentException("Cannot create abstract item: " + id);
        }
        if (definition.abstractDefinition() && (definition.render() == null || definition.render().model() == null
                || definition.material() == null)) {
            throw new IllegalArgumentException("Abstract item has no renderable model and material: " + id);
        }
        RenderAllocation allocation = snapshot.renderAllocations().get(id).orElse(null);
        return platform.items().createItem(definition, quantity, allocation);
    }

    public Optional<ContentID> identify(ItemStack stack) {
        return platform.items().getContentId(stack);
    }
}
