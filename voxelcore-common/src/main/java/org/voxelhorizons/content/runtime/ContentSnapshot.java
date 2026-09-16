package org.voxelhorizons.content.runtime;

import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.render.RenderAllocationRegistry;
import org.voxelhorizons.content.block.BlockAllocationRegistry;
import org.voxelhorizons.content.block.BlockDefinitionRegistry;

import java.util.Objects;

/** Immutable set of compiled content and render allocations published to the live runtime. */
public final class ContentSnapshot {
    private final long revision;
    private final ItemDefinitionRegistry items;
    private final RenderAllocationRegistry renderAllocations;
    private final BlockDefinitionRegistry blocks;
    private final BlockAllocationRegistry blockAllocations;

    public ContentSnapshot(long revision, ItemDefinitionRegistry items) {
        this(revision, items, RenderAllocationRegistry.empty(), BlockDefinitionRegistry.empty(), BlockAllocationRegistry.empty());
    }

    public ContentSnapshot(long revision, ItemDefinitionRegistry items, RenderAllocationRegistry renderAllocations) {
        this(revision, items, renderAllocations, BlockDefinitionRegistry.empty(), BlockAllocationRegistry.empty());
    }

    public ContentSnapshot(long revision, ItemDefinitionRegistry items, RenderAllocationRegistry renderAllocations,
                           BlockDefinitionRegistry blocks, BlockAllocationRegistry blockAllocations) {
        if (revision < 0L) {
            throw new IllegalArgumentException("revision cannot be negative");
        }
        this.revision = revision;
        this.items = Objects.requireNonNull(items, "items");
        this.renderAllocations = Objects.requireNonNull(renderAllocations, "renderAllocations");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.blockAllocations = Objects.requireNonNull(blockAllocations, "blockAllocations");
    }

    public long revision() { return revision; }
    public ItemDefinitionRegistry items() { return items; }
    public RenderAllocationRegistry renderAllocations() { return renderAllocations; }
    public BlockDefinitionRegistry blocks() { return blocks; }
    public BlockAllocationRegistry blockAllocations() { return blockAllocations; }
}
