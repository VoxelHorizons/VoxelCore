package org.voxelhorizons.content.runtime;

import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.render.RenderAllocationRegistry;

import java.util.Objects;

/** Immutable set of compiled content and render allocations published to the live runtime. */
public final class ContentSnapshot {
    private final long revision;
    private final ItemDefinitionRegistry items;
    private final RenderAllocationRegistry renderAllocations;

    public ContentSnapshot(long revision, ItemDefinitionRegistry items) {
        this(revision, items, RenderAllocationRegistry.empty());
    }

    public ContentSnapshot(long revision, ItemDefinitionRegistry items, RenderAllocationRegistry renderAllocations) {
        if (revision < 0L) {
            throw new IllegalArgumentException("revision cannot be negative");
        }
        this.revision = revision;
        this.items = Objects.requireNonNull(items, "items");
        this.renderAllocations = Objects.requireNonNull(renderAllocations, "renderAllocations");
    }

    public long revision() { return revision; }
    public ItemDefinitionRegistry items() { return items; }
    public RenderAllocationRegistry renderAllocations() { return renderAllocations; }
}
