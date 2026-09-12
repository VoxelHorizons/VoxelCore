package org.voxelhorizons.content.render;

import java.util.Objects;

/** Stable render allocation owned by a content ID rather than a runtime adapter. */
public final class RenderAllocation {
    private final int customModelData;
    private final String model;
    private final boolean active;

    public RenderAllocation(int customModelData, String model, boolean active) {
        if (customModelData < 0) throw new IllegalArgumentException("customModelData cannot be negative");
        this.customModelData = customModelData;
        this.model = Objects.requireNonNull(model, "model");
        this.active = active;
    }

    public int customModelData() { return customModelData; }
    public String model() { return model; }
    public boolean active() { return active; }

    public RenderAllocation withModelAndActive(String nextModel, boolean nextActive) {
        return new RenderAllocation(customModelData, nextModel, nextActive);
    }
}
