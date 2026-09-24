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

    /** Preflight one compiled definition against the active platform without publishing it. */
    default void validateDefinition(ItemDefinition definition, RenderAllocation allocation) {
        createItem(definition, 1, allocation);
    }

    Optional<ContentID> getContentId(ItemStack stack);
    ItemStack setContentId(ItemStack stack, ContentID id);

    default boolean supportsDynamicItemColors() { return false; }

    /** Reads one structured Custom Model Data colour. Unsupported platforms return empty. */
    default Optional<Integer> getCustomModelColor(ItemStack stack, RenderAllocation allocation, String key) {
        return Optional.empty();
    }

    /** Writes one structured Custom Model Data colour. Unsupported platforms reject dynamic tinting. */
    default ItemStack setCustomModelColor(ItemStack stack, RenderAllocation allocation, String key, int rgb) {
        throw new UnsupportedOperationException("Dynamic item tinting requires Minecraft 1.21.4 or newer");
    }

    static Optional<ContentID> parseStoredContentId(String value) {
        if (value == null || value.trim().isEmpty()) return Optional.empty();
        try {
            return Optional.of(ContentID.parse(value, "voxelhorizons"));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
