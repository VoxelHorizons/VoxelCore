package org.voxelhorizons.platform.v26_2;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.CustomModelDataDefinition;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.content.render.RenderAllocation;
import org.voxelhorizons.content.render.StructuredModelDataAllocation;
import org.voxelhorizons.platform.item.ItemMetadataSupport;
import org.voxelhorizons.platform.item.ItemPlatformAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class v26_2_ItemAdapter implements ItemPlatformAdapter {
    private final NamespacedKey contentIdKey;
    public v26_2_ItemAdapter(Plugin plugin) { this.contentIdKey = new NamespacedKey(plugin, "content_id"); }
    @Override public boolean supportsDynamicItemColors() { return true; }

    @Override public ItemStack createItem(ItemDefinition definition, int quantity) { return createItem(definition, quantity, null); }

    @Override public ItemStack createItem(ItemDefinition definition, int quantity, RenderAllocation allocation) {
        Material material = Material.matchMaterial(definition.material().replace("minecraft:", ""));
        if (material == null) throw new IllegalArgumentException("Unknown Minecraft material: " + definition.material());
        ItemStack stack = new ItemStack(material, quantity);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            ItemMetadataSupport.applyText(meta, definition);
            ItemMetadataSupport.applyCommon(meta, definition.render());
            if (definition.render() != null) {
                if (definition.render().durability() != null) applyDurability(meta, material, definition.render().durability().intValue(), definition);
                if (definition.render().model() != null && !definition.render().model().trim().isEmpty()) {
                    NamespacedKey model = NamespacedKey.fromString(definition.render().model());
                    if (model == null) throw new IllegalArgumentException("Invalid item model key for " + definition.id() + ": " + definition.render().model());
                    meta.setItemModel(model);
                }
                if (definition.render().customModelData() != null) applyCustomModelData(meta, definition.render().customModelData(), allocation, definition);
            }
            meta.getPersistentDataContainer().set(contentIdKey, PersistentDataType.STRING, definition.id().toString());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static void applyDurability(ItemMeta meta, Material material, int durability, ItemDefinition definition) {
        int max = material.getMaxDurability();
        if (!(meta instanceof Damageable) || max <= 0) throw new IllegalArgumentException("durability requires a damageable material for " + definition.id());
        if (durability < 0 || durability >= max) throw new IllegalArgumentException("durability " + durability + " is invalid for " + definition.id() + "; expected 0.." + (max - 1));
        ((Damageable) meta).setDamage(durability);
    }

    private static void applyCustomModelData(ItemMeta meta, CustomModelDataDefinition data, RenderAllocation allocation, ItemDefinition definition) {
        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        if (data.isNumeric()) {
            component.setFloats(Collections.singletonList(data.numeric().floatValue()));
        } else if (allocation != null) {
            applyStructured(component, data, allocation.structuredModelData(), definition);
        } else {
            applyStructuredFallback(component, data);
        }
        meta.setCustomModelDataComponent(component);
    }

    private static void applyStructured(CustomModelDataComponent component, CustomModelDataDefinition data,
                                        StructuredModelDataAllocation indices, ItemDefinition definition) {
        List<Float> floats = filledFloats(indices.size(CustomModelDataDefinition.ValueType.FLOAT));
        List<Boolean> flags = filledFlags(indices.size(CustomModelDataDefinition.ValueType.FLAG));
        List<String> strings = filledStrings(indices.size(CustomModelDataDefinition.ValueType.STRING));
        List<Color> colors = filledColors(indices.size(CustomModelDataDefinition.ValueType.COLOR));
        for (Map.Entry<String, CustomModelDataDefinition.Value> entry : data.structuredValues().entrySet()) {
            CustomModelDataDefinition.Value value = entry.getValue();
            Integer index = indices.index(value.type(), entry.getKey()).orElse(null);
            if (index == null) throw new IllegalArgumentException("Missing stable structured custom_model_data index for '" + entry.getKey() + "' on " + definition.id());
            switch (value.type()) {
                case FLOAT: floats.set(index.intValue(), Float.valueOf(value.floatValue())); break;
                case FLAG: flags.set(index.intValue(), Boolean.valueOf(value.booleanValue())); break;
                case STRING: strings.set(index.intValue(), value.stringValue()); break;
                case COLOR: colors.set(index.intValue(), Color.fromRGB(value.colorRgb())); break;
                default: throw new IllegalArgumentException("Unsupported structured custom model data type " + value.type());
            }
        }
        component.setFloats(floats); component.setFlags(flags); component.setStrings(strings); component.setColors(colors);
    }

    private static void applyStructuredFallback(CustomModelDataComponent component, CustomModelDataDefinition data) {
        List<Float> floats = new ArrayList<Float>();
        for (Map.Entry<String, CustomModelDataDefinition.Value> entry : data.valuesOfType(CustomModelDataDefinition.ValueType.FLOAT)) floats.add(Float.valueOf(entry.getValue().floatValue()));
        List<Boolean> flags = new ArrayList<Boolean>();
        for (Map.Entry<String, CustomModelDataDefinition.Value> entry : data.valuesOfType(CustomModelDataDefinition.ValueType.FLAG)) flags.add(Boolean.valueOf(entry.getValue().booleanValue()));
        List<String> strings = new ArrayList<String>();
        for (Map.Entry<String, CustomModelDataDefinition.Value> entry : data.valuesOfType(CustomModelDataDefinition.ValueType.STRING)) strings.add(entry.getValue().stringValue());
        List<Color> colors = new ArrayList<Color>();
        for (Map.Entry<String, CustomModelDataDefinition.Value> entry : data.valuesOfType(CustomModelDataDefinition.ValueType.COLOR)) colors.add(Color.fromRGB(entry.getValue().colorRgb()));
        component.setFloats(floats); component.setFlags(flags); component.setStrings(strings); component.setColors(colors);
    }

    private static List<Float> filledFloats(int size) { List<Float> values = new ArrayList<Float>(size); for (int i=0;i<size;i++) values.add(Float.valueOf(0.0f)); return values; }
    private static List<Boolean> filledFlags(int size) { List<Boolean> values = new ArrayList<Boolean>(size); for (int i=0;i<size;i++) values.add(Boolean.FALSE); return values; }
    private static List<String> filledStrings(int size) { List<String> values = new ArrayList<String>(size); for (int i=0;i<size;i++) values.add(""); return values; }
    private static List<Color> filledColors(int size) { List<Color> values = new ArrayList<Color>(size); for (int i=0;i<size;i++) values.add(Color.WHITE); return values; }

    @Override public Optional<ContentID> getContentId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return Optional.empty();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return Optional.empty();
        return ItemPlatformAdapter.parseStoredContentId(meta.getPersistentDataContainer().get(contentIdKey, PersistentDataType.STRING));
    }

    @Override public ItemStack setContentId(ItemStack stack, ContentID id) {
        if (stack == null) throw new IllegalArgumentException("stack cannot be null");
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(contentIdKey, PersistentDataType.STRING, id.toString());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override public Optional<Integer> getCustomModelColor(ItemStack stack, RenderAllocation allocation, String key) {
        if (stack == null || !stack.hasItemMeta() || allocation == null) return Optional.empty();
        Integer index = allocation.structuredModelData().index(CustomModelDataDefinition.ValueType.COLOR, key).orElse(null);
        if (index == null) return Optional.empty();
        List<Color> colors = stack.getItemMeta().getCustomModelDataComponent().getColors();
        return index.intValue() < colors.size() ? Optional.of(Integer.valueOf(colors.get(index.intValue()).asRGB())) : Optional.<Integer>empty();
    }

    @Override public ItemStack setCustomModelColor(ItemStack stack, RenderAllocation allocation, String key, int rgb) {
        if (stack == null || allocation == null) throw new IllegalArgumentException("Missing item or render allocation");
        Integer index = allocation.structuredModelData().index(CustomModelDataDefinition.ValueType.COLOR, key).orElse(null);
        if (index == null) throw new IllegalArgumentException("No structured color key '" + key + "' is allocated");
        ItemMeta meta = stack.getItemMeta();
        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        List<Color> colors = new ArrayList<Color>(component.getColors());
        while (colors.size() <= index.intValue()) colors.add(Color.WHITE);
        colors.set(index.intValue(), Color.fromRGB(rgb & 0xFFFFFF));
        component.setColors(colors); meta.setCustomModelDataComponent(component); stack.setItemMeta(meta); return stack;
    }
}
