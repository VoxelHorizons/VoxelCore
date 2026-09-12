package org.voxelhorizons.content.runtime;

import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.render.RenderAllocationRegistry;

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
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(renderAllocations, "renderAllocations");
        ContentSnapshot current = active.get();
        ContentSnapshot next = new ContentSnapshot(current.revision() + 1L, items, renderAllocations);
        active.set(next);
        return next;
    }
}
