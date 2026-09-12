package org.voxelhorizons.content.item;

public final class RawItemRenderDefinition {
    private final String model;
    private final Integer legacyCustomModelData;

    public RawItemRenderDefinition(String model, Integer legacyCustomModelData) {
        this.model = model;
        this.legacyCustomModelData = legacyCustomModelData;
    }

    public String model() {
        return model;
    }

    public Integer legacyCustomModelData() {
        return legacyCustomModelData;
    }
}
