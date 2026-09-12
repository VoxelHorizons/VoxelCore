package org.voxelhorizons.content.runtime;

import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.render.RenderAllocationRegistry;

/** Validates a fully compiled candidate before it is persisted or published. */
public interface ContentSnapshotValidator {
    void validate(ItemDefinitionRegistry items, RenderAllocationRegistry renderAllocations);
}
