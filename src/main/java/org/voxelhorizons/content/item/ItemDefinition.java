package org.voxelhorizons.content.item;

import org.voxelhorizons.content.ContentID;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ItemDefinition(
        ContentID id,
        ItemType type,
        String material,
        String displayName,
        List<String> lore,
        boolean bound,
        Optional<ContentID> parent,
        ItemRenderDefinition render,
        Map<String, Object> properties
) {

    public ItemDefinition {
        lore = List.copyOf(lore);
        properties = Map.copyOf(properties);
    }
}