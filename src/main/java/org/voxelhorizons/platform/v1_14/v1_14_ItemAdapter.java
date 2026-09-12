package org.voxelhorizons.platform.v1_14;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.platform.item.ItemPlatformAdapter;

import java.util.Optional;

public final class v1_14_ItemAdapter implements ItemPlatformAdapter {

    private final NamespacedKey contentIdKey;

    public v1_14_ItemAdapter(VoxelCore plugin) {
        this.contentIdKey = new NamespacedKey(plugin, "content_id");
    }

    @Override
    public ItemStack createItem(
            ItemDefinition definition,
            int quantity
    ) {
        Material material = Material.matchMaterial(
                definition.material().replace("minecraft:", "")
        );

        if (material == null) {
            throw new IllegalArgumentException(
                    "Unknown Minecraft material: " + definition.material()
            );
        }

        ItemStack stack = new ItemStack(material, quantity);

        ItemMeta meta = stack.getItemMeta();

        if (meta != null) {

            if (definition.displayName() != null) {
                meta.setDisplayName(definition.displayName());
            }

            if (definition.lore() != null) {
                meta.setLore(definition.lore());
            }

            meta.getPersistentDataContainer().set(
                    contentIdKey,
                    PersistentDataType.STRING,
                    definition.id().toString()
            );

            stack.setItemMeta(meta);
        }

        return stack;
    }

    @Override
    public Optional<ContentID> getContentId(ItemStack stack) {

        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }

        ItemMeta meta = stack.getItemMeta();

        String value = meta
                .getPersistentDataContainer()
                .get(contentIdKey, PersistentDataType.STRING);

        if (value == null) {
            return Optional.empty();
        }

        return Optional.of(
                ContentID.parse(value, "voxelhorizons")
        );
    }

    @Override
    public void setContentId(
            ItemStack stack,
            ContentID id
    ) {
        ItemMeta meta = stack.getItemMeta();

        if (meta == null) {
            return;
        }

        meta.getPersistentDataContainer().set(
                contentIdKey,
                PersistentDataType.STRING,
                id.toString()
        );

        stack.setItemMeta(meta);
    }
}