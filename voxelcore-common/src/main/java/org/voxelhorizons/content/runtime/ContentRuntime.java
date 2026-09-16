package org.voxelhorizons.content.runtime;

import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.render.RenderAllocationRegistry;
import org.voxelhorizons.content.block.BlockAllocationRegistry;
import org.voxelhorizons.content.block.BlockDefinitionRegistry;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Owns the currently active immutable content snapshot. */
public final class ContentRuntime {
    private final AtomicReference<ContentSnapshot> active;

    public ContentRuntime(ContentSnapshot initial) {
        this.active = new AtomicReference<ContentSnapshot>(Objects.requireNonNull(initial, "initial"));
    }

    public ContentSnapshot current() { return active.get(); }

    public synchronized ContentSnapshot publish(ItemDefinitionRegistry items) {
        return publish(items, active.get().renderAllocations());
    }

    /** Atomically publishes definitions and the matching stable render allocations. */
    public synchronized ContentSnapshot publish(ItemDefinitionRegistry items, RenderAllocationRegistry renderAllocations) {
        return publish(items, renderAllocations, active.get().blocks(), active.get().blockAllocations());
    }

    public synchronized ContentSnapshot publish(ItemDefinitionRegistry items, RenderAllocationRegistry renderAllocations,
                                                BlockDefinitionRegistry blocks, BlockAllocationRegistry blockAllocations) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(renderAllocations, "renderAllocations");
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(blockAllocations, "blockAllocations");
        ContentSnapshot current = active.get();
        ContentSnapshot next = new ContentSnapshot(current.revision() + 1L, items, renderAllocations, blocks, blockAllocations);
        active.set(next);
        return next;
    }
}
