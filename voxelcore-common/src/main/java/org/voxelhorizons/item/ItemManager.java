package org.voxelhorizons.item;

import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.content.render.RenderAllocation;
import org.voxelhorizons.content.runtime.ContentRuntime;
import org.voxelhorizons.content.runtime.ContentSnapshot;
import org.voxelhorizons.platform.VersionAdapter;

import java.util.Optional;
import java.util.Map;

public final class ItemManager {

    private final ContentRuntime content;
    private final VersionAdapter platform;

    public ItemManager(ContentRuntime content, VersionAdapter platform) {
        this.content = content;
        this.platform = platform;
    }

    public Optional<ItemDefinition> getDefinition(ContentID id) {
        return content.current().items().get(id);
    }

    public boolean hasItem(ContentID id) {
        return content.current().items().contains(id);
    }

    public ItemStack createItem(ContentID id) {
        return createItem(id, 1);
    }

    public ItemStack createItem(ContentID id, int quantity) {
        return create(id, quantity, false);
    }

    /** Creates an internal display-only model. Abstract definitions are never exposed through give commands. */
    public ItemStack createRenderItem(ContentID id) {
        return create(id, 1, true);
    }

    /** Creates a display-only model while preserving a source item's mutable tint. */
    public ItemStack createRenderItem(ContentID id, ItemStack source) {
        ItemStack rendered = createRenderItem(id);
        Optional<Integer> color = getDyeColor(source);
        return color.isPresent() ? setDyeColor(rendered, color.get().intValue()) : rendered;
    }

    public boolean isDyeable(ItemStack stack) {
        if (!platform.items().supportsDynamicItemColors()) return false;
        Optional<ContentID> id = identify(stack);
        return id.isPresent() && dyeable(getDefinition(id.get()).orElse(null)) != null;
    }

    public Optional<Integer> getDyeColor(ItemStack stack) {
        if (!platform.items().supportsDynamicItemColors()) return Optional.empty();
        Optional<ContentID> id = identify(stack);
        if (!id.isPresent()) return Optional.empty();
        ItemDefinition definition = getDefinition(id.get()).orElse(null);
        Dyeable dyeable = dyeable(definition);
        if (dyeable == null) return Optional.empty();
        RenderAllocation allocation = content.current().renderAllocations().get(id.get()).orElse(null);
        Optional<Integer> stored = platform.items().getCustomModelColor(stack, allocation, dyeable.key);
        return stored.isPresent() ? stored : Optional.of(Integer.valueOf(dyeable.defaultColor));
    }

    public ItemStack setDyeColor(ItemStack stack, int rgb) {
        if (!platform.items().supportsDynamicItemColors())
            throw new UnsupportedOperationException("Dynamic item tinting requires Minecraft 1.21.4 or newer");
        Optional<ContentID> id = identify(stack);
        if (!id.isPresent()) throw new IllegalArgumentException("Item is not managed by VoxelCore");
        ItemDefinition definition = getDefinition(id.get()).orElse(null);
        Dyeable dyeable = dyeable(definition);
        if (dyeable == null) throw new IllegalArgumentException("Item is not dyeable: " + id.get());
        RenderAllocation allocation = content.current().renderAllocations().get(id.get()).orElse(null);
        return platform.items().setCustomModelColor(stack, allocation, dyeable.key, rgb & 0xFFFFFF);
    }

    private ItemStack create(ContentID id, int quantity, boolean allowAbstract) {
        ContentSnapshot snapshot = content.current();
        ItemDefinition definition = snapshot.items().get(id).orElseThrow(() ->
                new IllegalArgumentException("Unknown item: " + id)
        );
        if (definition.abstractDefinition() && !allowAbstract) {
            throw new IllegalArgumentException("Cannot create abstract item: " + id);
        }
        if (definition.abstractDefinition() && (definition.render() == null || definition.render().model() == null
                || definition.material() == null)) {
            throw new IllegalArgumentException("Abstract item has no renderable model and material: " + id);
        }
        RenderAllocation allocation = snapshot.renderAllocations().get(id).orElse(null);
        return platform.items().createItem(definition, quantity, allocation);
    }

    public Optional<ContentID> identify(ItemStack stack) {
        return platform.items().getContentId(stack);
    }

    @SuppressWarnings("unchecked")
    private static Dyeable dyeable(ItemDefinition definition) {
        if (definition == null) return null;
        Object raw = definition.properties().get("dyeable");
        if (!(raw instanceof Map)) return null;
        Map<String, Object> values = (Map<String, Object>) raw;
        String key = values.get("key") == null ? "color" : String.valueOf(values.get("key"));
        Object color = values.get("color");
        int rgb = color == null ? 0xFFFFFF : parseColor(String.valueOf(color));
        return new Dyeable(key, rgb);
    }

    private static int parseColor(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("#")) text = text.substring(1);
        if (!text.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Dyeable color must be #RRGGBB");
        return Integer.parseInt(text, 16);
    }

    private static final class Dyeable {
        private final String key; private final int defaultColor;
        private Dyeable(String key, int defaultColor) { this.key = key; this.defaultColor = defaultColor; }
    }
}
