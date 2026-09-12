package org.voxelhorizons.platform.v1_21_4;

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
import org.voxelhorizons.platform.item.ItemMetadataSupport;
import org.voxelhorizons.platform.item.ItemPlatformAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class v1_21_4_ItemAdapter implements ItemPlatformAdapter {
    private final NamespacedKey contentIdKey;
    public v1_21_4_ItemAdapter(Plugin plugin) { this.contentIdKey = new NamespacedKey(plugin, "content_id"); }

    @Override
    public ItemStack createItem(ItemDefinition definition, int quantity) {
        Material material = Material.matchMaterial(definition.material().replace("minecraft:", ""));
        if (material == null) throw new IllegalArgumentException("Unknown Minecraft material: " + definition.material());
        ItemStack stack = new ItemStack(material, quantity);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (definition.displayName() != null) meta.setDisplayName(definition.displayName());
            if (!definition.lore().isEmpty()) meta.setLore(definition.lore());
            ItemMetadataSupport.applyCommon(meta, definition.render());

            if (definition.render() != null) {
                if (definition.render().durability() != null) {
                    applyDurability(meta, material, definition.render().durability().intValue(), definition);
                }
                if (definition.render().model() != null && !definition.render().model().trim().isEmpty()) {
                    NamespacedKey model = NamespacedKey.fromString(definition.render().model());
                    if (model != null) meta.setItemModel(model);
                }
                if (definition.render().customModelData() != null) {
                    applyCustomModelData(meta, definition.render().customModelData());
                }
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

    private static void applyCustomModelData(ItemMeta meta, CustomModelDataDefinition data) {
        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        if (data.isNumeric()) {
            component.setFloats(Collections.singletonList(data.numeric().floatValue()));
        } else {
            List<Float> floats = new ArrayList<Float>();
            for (Map.Entry<String, CustomModelDataDefinition.Value> entry : data.valuesOfType(CustomModelDataDefinition.ValueType.FLOAT)) {
                floats.add(Float.valueOf(entry.getValue().floatValue()));
            }
            List<Boolean> flags = new ArrayList<Boolean>();
            for (Map.Entry<String, CustomModelDataDefinition.Value> entry : data.valuesOfType(CustomModelDataDefinition.ValueType.FLAG)) {
                flags.add(Boolean.valueOf(entry.getValue().booleanValue()));
            }
            List<String> strings = new ArrayList<String>();
            for (Map.Entry<String, CustomModelDataDefinition.Value> entry : data.valuesOfType(CustomModelDataDefinition.ValueType.STRING)) {
                strings.add(entry.getValue().stringValue());
            }
            List<Color> colors = new ArrayList<Color>();
            for (Map.Entry<String, CustomModelDataDefinition.Value> entry : data.valuesOfType(CustomModelDataDefinition.ValueType.COLOR)) {
                colors.add(Color.fromRGB(entry.getValue().colorRgb()));
            }
            component.setFloats(floats);
            component.setFlags(flags);
            component.setStrings(strings);
            component.setColors(colors);
        }
        meta.setCustomModelDataComponent(component);
    }

    @Override public Optional<ContentID> getContentId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return Optional.empty();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return Optional.empty();
        String value = meta.getPersistentDataContainer().get(contentIdKey, PersistentDataType.STRING);
        return value == null ? Optional.<ContentID>empty() : Optional.of(ContentID.parse(value, "voxelhorizons"));
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
}
