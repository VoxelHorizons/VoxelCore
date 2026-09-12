package org.voxelhorizons.content.item;

public final class ItemRenderDefinition {
    private final String model;
    private final Integer legacyCustomModelData;

    public ItemRenderDefinition(String model, Integer legacyCustomModelData) {
        this.model = model;
        this.legacyCustomModelData = legacyCustomModelData;
    }

    public String model() { return model; }
    public Integer legacyCustomModelData() { return legacyCustomModelData; }
}
