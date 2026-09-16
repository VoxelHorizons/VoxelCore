package org.voxelhorizons.content.block;

import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.action.EventActions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Nullable authoring form used while block inheritance is resolved. */
public final class RawBlockDefinition {
    private final ContentID id;
    private final ContentID parent;
    private final Boolean abstractDefinition;
    private final BlockMethod method;
    private final BlockModelPreset model;
    private final String displayName;
    private final String texture;
    private final Map<String, String> textures;
    private final Double hardness;
    private final Double blastResistance;
    private final Boolean explosionImmune;
    private final Boolean dropWhenMined;
    private final ContentID dropItem;
    private final ContentID silkTouchItem;
    private final EventActions events;

    public RawBlockDefinition(ContentID id, ContentID parent, Boolean abstractDefinition,
                              BlockMethod method, BlockModelPreset model, String texture,
                              Map<String, String> textures, Double hardness, Double blastResistance,
                              Boolean explosionImmune, Boolean dropWhenMined, ContentID dropItem,
                              ContentID silkTouchItem, EventActions events) {
        this(id, parent, abstractDefinition, method, model, null, texture, textures, hardness,
                blastResistance, explosionImmune, dropWhenMined, dropItem, silkTouchItem, events);
    }

    public RawBlockDefinition(ContentID id, ContentID parent, Boolean abstractDefinition,
                              BlockMethod method, BlockModelPreset model, String displayName, String texture,
                              Map<String, String> textures, Double hardness, Double blastResistance,
                              Boolean explosionImmune, Boolean dropWhenMined, ContentID dropItem,
                              ContentID silkTouchItem, EventActions events) {
        this.id = id;
        this.parent = parent;
        this.abstractDefinition = abstractDefinition;
        this.method = method;
        this.model = model;
        this.displayName = displayName;
        this.texture = texture;
        this.textures = textures == null ? null : Collections.unmodifiableMap(new LinkedHashMap<String, String>(textures));
        this.hardness = hardness;
        this.blastResistance = blastResistance;
        this.explosionImmune = explosionImmune;
        this.dropWhenMined = dropWhenMined;
        this.dropItem = dropItem;
        this.silkTouchItem = silkTouchItem;
        this.events = events;
    }

    public ContentID id() { return id; }
    public ContentID parent() { return parent; }
    public Boolean abstractDefinition() { return abstractDefinition; }
    public BlockMethod method() { return method; }
    public BlockModelPreset model() { return model; }
    public String displayName() { return displayName; }
    public String texture() { return texture; }
    public Map<String, String> textures() { return textures; }
    public Double hardness() { return hardness; }
    public Double blastResistance() { return blastResistance; }
    public Boolean explosionImmune() { return explosionImmune; }
    public Boolean dropWhenMined() { return dropWhenMined; }
    public ContentID dropItem() { return dropItem; }
    public ContentID silkTouchItem() { return silkTouchItem; }
    public EventActions events() { return events; }
}
