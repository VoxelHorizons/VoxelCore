package org.voxelhorizons.platform;

public record Capabilities(
        boolean persistentDataContainer,
        boolean numericCustomModelData,
        boolean displayEntities,
        boolean itemDataComponents,
        boolean structuredCustomModelData,
        boolean namespacedItemModels
) {
}