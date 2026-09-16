package org.voxelhorizons.content.runtime;

import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.render.RenderAllocationRegistry;
import org.voxelhorizons.content.block.BlockAllocationRegistry;
import org.voxelhorizons.content.block.BlockDefinitionRegistry;

/** Validates a fully compiled candidate before it is persisted or published. */
public interface ContentSnapshotValidator {
    void validate(ItemDefinitionRegistry items, RenderAllocationRegistry renderAllocations);

    default void validateBlocks(BlockDefinitionRegistry blocks, BlockAllocationRegistry blockAllocations) { }
}
