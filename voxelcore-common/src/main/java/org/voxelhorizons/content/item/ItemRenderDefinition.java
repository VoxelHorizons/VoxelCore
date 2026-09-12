package org.voxelhorizons.content.item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ItemRenderDefinition {
    private final String model;
    private final Boolean unbreakable;
    private final Integer durability;
    private final Map<String, Boolean> attributes;
    private final CustomModelDataDefinition customModelData;
    private final Map<String, Object> rule;

    public ItemRenderDefinition(String model, CustomModelDataDefinition customModelData) {
        this(model, null, null, null, customModelData, null);
    }

    public ItemRenderDefinition(String model,
                                Boolean unbreakable,
                                Integer durability,
                                Map<String, Boolean> attributes,
                                CustomModelDataDefinition customModelData) {
        this(model, unbreakable, durability, attributes, customModelData, null);
    }

    public ItemRenderDefinition(String model,
                                Boolean unbreakable,
                                Integer durability,
                                Map<String, Boolean> attributes,
                                CustomModelDataDefinition customModelData,
                                Map<String, Object> rule) {
        this.model = model;
        this.unbreakable = unbreakable;
        this.durability = durability;
        this.attributes = attributes == null
                ? Collections.<String, Boolean>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, Boolean>(attributes));
        this.customModelData = customModelData;
        this.rule = ImmutableData.map(rule);
    }

    public String model() { return model; }
    public Boolean unbreakable() { return unbreakable; }
    public Integer durability() { return durability; }
    public Map<String, Boolean> attributes() { return attributes; }
    public CustomModelDataDefinition customModelData() { return customModelData; }
    public Map<String, Object> rule() { return rule; }
}
