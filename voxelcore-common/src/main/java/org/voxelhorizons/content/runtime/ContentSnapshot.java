package org.voxelhorizons.content.runtime;

import org.voxelhorizons.content.item.ItemDefinitionRegistry;

import java.util.Objects;

/** Immutable set of compiled content published to the live runtime. */
public final class ContentSnapshot {
    private final long revision;
    private final ItemDefinitionRegistry items;

    public ContentSnapshot(long revision, ItemDefinitionRegistry items) {
        if (revision < 0L) {
            throw new IllegalArgumentException("revision cannot be negative");
        }
        this.revision = revision;
        this.items = Objects.requireNonNull(items, "items");
    }

    public long revision() {
        return revision;
    }

    public ItemDefinitionRegistry items() {
        return items;
    }
}
