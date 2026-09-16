package org.voxelhorizons.content.load;

import org.voxelhorizons.content.block.BlockDefinitionRegistry;
import org.voxelhorizons.content.item.ItemDefinitionRegistry;

/** Immutable compiled registries loaded from one content-root snapshot. */
public final class ContentDefinitions {
    private final ItemDefinitionRegistry items;
    private final BlockDefinitionRegistry blocks;

    public ContentDefinitions(ItemDefinitionRegistry items, BlockDefinitionRegistry blocks) {
        this.items = items;
        this.blocks = blocks;
    }

    public ItemDefinitionRegistry items() { return items; }
    public BlockDefinitionRegistry blocks() { return blocks; }
}
