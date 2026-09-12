package org.voxelhorizons.platform.v1_21_4;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.platform.item.ItemPlatformAdapter;

import java.util.Collections;
import java.util.Optional;

public final class v1_21_4_ItemAdapter implements ItemPlatformAdapter {

    private final NamespacedKey contentIdKey;

    public v1_21_4_ItemAdapter(Plugin plugin) {
        this.contentIdKey = new NamespacedKey(plugin, "content_id");
    }

    @Override
    public ItemStack createItem(ItemDefinition definition, int quantity) {
        Material material = Material.matchMaterial(definition.material().replace("minecraft:", ""));
        if (material == null) {
            throw new IllegalArgumentException("Unknown Minecraft material: " + definition.material());
        }

        ItemStack stack = new ItemStack(material, quantity);
        ItemMeta meta = stack.getItemMeta();

        if (meta != null) {
            if (definition.displayName() != null) meta.setDisplayName(definition.displayName());
            if (!definition.lore().isEmpty()) meta.setLore(definition.lore());

            if (definition.render() != null) {
                if (definition.render().model() != null && !definition.render().model().trim().isEmpty()) {
                    NamespacedKey model = NamespacedKey.fromString(definition.render().model());
                    if (model != null) {
                        meta.setItemModel(model);
                    }
                }

                if (definition.render().legacyCustomModelData() != null) {
                    CustomModelDataComponent component = meta.getCustomModelDataComponent();
                    component.setFloats(Collections.singletonList(definition.render().legacyCustomModelData().floatValue()));
                    meta.setCustomModelDataComponent(component);
                }
            }

            meta.getPersistentDataContainer().set(contentIdKey, PersistentDataType.STRING, definition.id().toString());
            stack.setItemMeta(meta);
        }

        return stack;
    }

    @Override
    public Optional<ContentID> getContentId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return Optional.empty();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return Optional.empty();
        String value = meta.getPersistentDataContainer().get(contentIdKey, PersistentDataType.STRING);
        return value == null ? Optional.<ContentID>empty() : Optional.of(ContentID.parse(value, "voxelhorizons"));
    }

    @Override
    public ItemStack setContentId(ItemStack stack, ContentID id) {
        if (stack == null) throw new IllegalArgumentException("stack cannot be null");
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(contentIdKey, PersistentDataType.STRING, id.toString());
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
