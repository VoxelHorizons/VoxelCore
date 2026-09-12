package org.voxelhorizons.platform.v1_19_4;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.CustomModelDataDefinition;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.platform.item.ItemPlatformAdapter;

import java.util.Optional;

public final class v1_19_4_ItemAdapter implements ItemPlatformAdapter {
    private final NamespacedKey contentIdKey;
    public v1_19_4_ItemAdapter(Plugin plugin) { this.contentIdKey = new NamespacedKey(plugin, "content_id"); }

    @Override
    public ItemStack createItem(ItemDefinition definition, int quantity) {
        Material material = Material.matchMaterial(definition.material().replace("minecraft:", ""));
        if (material == null) throw new IllegalArgumentException("Unknown Minecraft material: " + definition.material());
        ItemStack stack = new ItemStack(material, quantity);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (definition.displayName() != null) meta.setDisplayName(definition.displayName());
            if (!definition.lore().isEmpty()) meta.setLore(definition.lore());
            if (definition.render() != null && definition.render().customModelData() != null) {
                CustomModelDataDefinition data = definition.render().customModelData();
                if (!data.isNumeric()) throw new IllegalArgumentException("Structured custom_model_data requires Minecraft 1.21.4+ for " + definition.id());
                meta.setCustomModelData(data.numeric());
            }
            meta.getPersistentDataContainer().set(contentIdKey, PersistentDataType.STRING, definition.id().toString());
            stack.setItemMeta(meta);
        }
        return stack;
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
