package org.voxelhorizons.content.runtime;

import org.voxelhorizons.content.item.ItemDefinitionRegistry;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Owns the currently active immutable content snapshot. */
public final class ContentRuntime {
    private final AtomicReference<ContentSnapshot> active;

    public ContentRuntime(ContentSnapshot initial) {
        this.active = new AtomicReference<ContentSnapshot>(Objects.requireNonNull(initial, "initial"));
    }

    public ContentSnapshot current() {
        return active.get();
    }

    /**
     * Atomically publishes a fully compiled item registry as the next revision.
     * The existing snapshot remains active until this method is called.
     */
    public synchronized ContentSnapshot publish(ItemDefinitionRegistry items) {
        Objects.requireNonNull(items, "items");
        ContentSnapshot current = active.get();
        ContentSnapshot next = new ContentSnapshot(current.revision() + 1L, items);
        active.set(next);
        return next;
    }
}
