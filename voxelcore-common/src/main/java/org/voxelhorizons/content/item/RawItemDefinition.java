package org.voxelhorizons.content.item;

import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.action.EventActions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Authoring/intermediate item representation used before inheritance is resolved.
 * Nullable fields mean "omitted" and therefore inherit from the parent.
 */
public final class RawItemDefinition {
    private final ContentID id;
    private final ContentID parent;
    private final ItemType type;
    private final String material;
    private final String displayName;
    private final List<String> lore;
    private final Boolean bound;
    private final Boolean abstractDefinition;
    private final RawItemRenderDefinition render;
    private final Map<String, Object> properties;
    private final EventActions events;

    public RawItemDefinition(ContentID id,
                             ContentID parent,
                             ItemType type,
                             String material,
                             String displayName,
                             List<String> lore,
                             Boolean bound,
                             RawItemRenderDefinition render,
                             Map<String, Object> properties) {
        this(id, parent, type, material, displayName, lore, bound, null, render, properties);
    }

    public RawItemDefinition(ContentID id,
                             ContentID parent,
                             ItemType type,
                             String material,
                             String displayName,
                             List<String> lore,
                             Boolean bound,
                             Boolean abstractDefinition,
                             RawItemRenderDefinition render,
                             Map<String, Object> properties) {
        this(id, parent, type, material, displayName, lore, bound, abstractDefinition, render, properties, null);
    }

    public RawItemDefinition(ContentID id,
                             ContentID parent,
                             ItemType type,
                             String material,
                             String displayName,
                             List<String> lore,
                             Boolean bound,
                             Boolean abstractDefinition,
                             RawItemRenderDefinition render,
                             Map<String, Object> properties,
                             EventActions events) {
        this.id = id;
        this.parent = parent;
        this.type = type;
        this.material = material;
        this.displayName = displayName;
        this.lore = lore == null ? null : Collections.unmodifiableList(new ArrayList<String>(lore));
        this.bound = bound;
        this.abstractDefinition = abstractDefinition;
        this.render = render;
        this.properties = properties == null ? null : Collections.unmodifiableMap(new HashMap<String, Object>(properties));
        this.events = events;
    }

    public ContentID id() { return id; }
    public ContentID parent() { return parent; }
    public ItemType type() { return type; }
    public String material() { return material; }
    public String displayName() { return displayName; }
    public List<String> lore() { return lore; }
    public Boolean bound() { return bound; }
    public Boolean abstractDefinition() { return abstractDefinition; }
    public RawItemRenderDefinition render() { return render; }
    public Map<String, Object> properties() { return properties; }
    public EventActions events() { return events; }
}
