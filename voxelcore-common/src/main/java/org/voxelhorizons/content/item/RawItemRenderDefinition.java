package org.voxelhorizons.content.item;

public final class RawItemRenderDefinition {
    private final String model;
    private final CustomModelDataDefinition customModelData;

    public RawItemRenderDefinition(String model, CustomModelDataDefinition customModelData) {
        this.model = model;
        this.customModelData = customModelData;
    }

    public String model() { return model; }
    public CustomModelDataDefinition customModelData() { return customModelData; }
}
