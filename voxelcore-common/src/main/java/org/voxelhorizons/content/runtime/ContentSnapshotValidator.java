package org.voxelhorizons.content.runtime;

import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.render.RenderAllocationRegistry;
import org.voxelhorizons.content.block.BlockAllocationRegistry;
import org.voxelhorizons.content.block.BlockDefinitionRegistry;

/** Validates a fully compiled candidate before it is persisted or published. */
public interface ContentSnapshotValidator {
    void validate(ItemDefinitionRegistry items, RenderAllocationRegistry renderAllocations);

    default void validateBlocks(BlockDefinitionRegistry blocks, BlockAllocationRegistry blockAllocations) { }

    /**
     * Validates the block actions against the exact candidate item registry, rather
     * than reloading YAML while validating a prepared snapshot. Old implementors
     * continue to work through the two-argument default.
     */
    default void validateBlocks(BlockDefinitionRegistry blocks, BlockAllocationRegistry blockAllocations,
                                ItemDefinitionRegistry items) {
        validateBlocks(blocks, blockAllocations);
    }
}
