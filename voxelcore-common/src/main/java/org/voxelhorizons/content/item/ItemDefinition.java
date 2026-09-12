package org.voxelhorizons.content.item;

import org.voxelhorizons.content.ContentID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ItemDefinition {
    private final ContentID id;
    private final ItemType type;
    private final String material;
    private final String displayName;
    private final List<String> lore;
    private final boolean bound;
    private final Optional<ContentID> parent;
    private final ItemRenderDefinition render;
    private final Map<String, Object> properties;

    public ItemDefinition(ContentID id,
                          ItemType type,
                          String material,
                          String displayName,
                          List<String> lore,
                          boolean bound,
                          Optional<ContentID> parent,
                          ItemRenderDefinition render,
                          Map<String, Object> properties) {
        this.id = id;
        this.type = type;
        this.material = material;
        this.displayName = displayName;
        this.lore = Collections.unmodifiableList(new ArrayList<String>(lore == null ? Collections.<String>emptyList() : lore));
        this.bound = bound;
        this.parent = parent == null ? Optional.<ContentID>empty() : parent;
        this.render = render;
        this.properties = ImmutableData.map(properties);
    }

    public ContentID id() { return id; }
    public ItemType type() { return type; }
    public String material() { return material; }
    public String displayName() { return displayName; }
    public List<String> lore() { return lore; }
    public boolean bound() { return bound; }
    public Optional<ContentID> parent() { return parent; }
    public ItemRenderDefinition render() { return render; }
    public Map<String, Object> properties() { return properties; }
}
