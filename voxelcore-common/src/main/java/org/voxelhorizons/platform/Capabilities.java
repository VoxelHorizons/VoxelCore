package org.voxelhorizons.platform;

public final class Capabilities {
    private final boolean persistentDataContainer;
    private final boolean numericCustomModelData;
    private final boolean displayEntities;
    private final boolean itemDataComponents;
    private final boolean structuredCustomModelData;
    private final boolean namespacedItemModels;

    public Capabilities(boolean persistentDataContainer,
                        boolean numericCustomModelData,
                        boolean displayEntities,
                        boolean itemDataComponents,
                        boolean structuredCustomModelData,
                        boolean namespacedItemModels) {
        this.persistentDataContainer = persistentDataContainer;
        this.numericCustomModelData = numericCustomModelData;
        this.displayEntities = displayEntities;
        this.itemDataComponents = itemDataComponents;
        this.structuredCustomModelData = structuredCustomModelData;
        this.namespacedItemModels = namespacedItemModels;
    }

    public boolean persistentDataContainer() { return persistentDataContainer; }
    public boolean numericCustomModelData() { return numericCustomModelData; }
    public boolean displayEntities() { return displayEntities; }
    public boolean itemDataComponents() { return itemDataComponents; }
    public boolean structuredCustomModelData() { return structuredCustomModelData; }
    public boolean namespacedItemModels() { return namespacedItemModels; }
}
