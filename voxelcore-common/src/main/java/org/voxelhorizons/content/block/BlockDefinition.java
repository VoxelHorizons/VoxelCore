package org.voxelhorizons.content.block;

import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.action.EventActions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Immutable compiled definition for a real, carrier-backed custom block. */
public final class BlockDefinition {
    private final ContentID id;
    private final Optional<ContentID> parent;
    private final boolean abstractDefinition;
    private final BlockMethod method;
    private final BlockModelPreset model;
    private final String displayName;
    private final Map<String, String> textures;
    private final double hardness;
    private final double blastResistance;
    private final boolean explosionImmune;
    private final boolean dropWhenMined;
    private final ContentID dropItem;
    private final ContentID silkTouchItem;
    private final EventActions events;

    public BlockDefinition(ContentID id, Optional<ContentID> parent, boolean abstractDefinition,
                           BlockMethod method, BlockModelPreset model, Map<String, String> textures,
                           double hardness, double blastResistance, boolean explosionImmune,
                           boolean dropWhenMined, ContentID dropItem, ContentID silkTouchItem,
                           EventActions events) {
        this(id, parent, abstractDefinition, method, model, textures, null, hardness, blastResistance,
                explosionImmune, dropWhenMined, dropItem, silkTouchItem, events);
    }

    public BlockDefinition(ContentID id, Optional<ContentID> parent, boolean abstractDefinition,
                           BlockMethod method, BlockModelPreset model, Map<String, String> textures,
                           String displayName,
                           double hardness, double blastResistance, boolean explosionImmune,
                           boolean dropWhenMined, ContentID dropItem, ContentID silkTouchItem,
                           EventActions events) {
        this.id = id;
        this.parent = parent == null ? Optional.<ContentID>empty() : parent;
        this.abstractDefinition = abstractDefinition;
        this.method = method;
        this.model = model;
        this.displayName = displayName;
        this.textures = Collections.unmodifiableMap(new LinkedHashMap<String, String>(textures));
        this.hardness = hardness;
        this.blastResistance = blastResistance;
        this.explosionImmune = explosionImmune;
        this.dropWhenMined = dropWhenMined;
        this.dropItem = dropItem;
        this.silkTouchItem = silkTouchItem;
        this.events = events == null ? EventActions.empty() : events;
    }

    public ContentID id() { return id; }
    public Optional<ContentID> parent() { return parent; }
    public boolean abstractDefinition() { return abstractDefinition; }
    public BlockMethod method() { return method; }
    public BlockModelPreset model() { return model; }
    public String displayName() { return displayName; }
    public Map<String, String> textures() { return textures; }
    public double hardness() { return hardness; }
    public double blastResistance() { return blastResistance; }
    public boolean explosionImmune() { return explosionImmune; }
    public boolean dropWhenMined() { return dropWhenMined; }
    public ContentID dropItem() { return dropItem; }
    public ContentID silkTouchItem() { return silkTouchItem; }
    public EventActions events() { return events; }
}
