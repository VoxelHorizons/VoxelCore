package org.voxelhorizons.content.item;

public final class ItemRenderDefinition {
    private final String model;
    private final CustomModelDataDefinition customModelData;

    public ItemRenderDefinition(String model, CustomModelDataDefinition customModelData) {
        this.model = model;
        this.customModelData = customModelData;
    }

    public String model() { return model; }
    public CustomModelDataDefinition customModelData() { return customModelData; }
}
