package org.voxelhorizons.platform.v1_12;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.platform.item.ItemPlatformAdapter;

import java.util.Optional;

public final class v1_12_ItemAdapter implements ItemPlatformAdapter {

    @Override
    public ItemStack createItem(ItemDefinition definition, int quantity) {
        Material material = Material.matchMaterial(definition.material().replace("minecraft:", ""));
        if (material == null) {
            throw new IllegalArgumentException("Unknown Minecraft material: " + definition.material());
        }

        ItemStack stack = new ItemStack(material, quantity);
        ItemMeta meta = stack.getItemMeta();

        if (meta != null) {
            if (definition.displayName() != null) {
                meta.setDisplayName(definition.displayName());
            }
            if (!definition.lore().isEmpty()) {
                meta.setLore(definition.lore());
            }
            stack.setItemMeta(meta);
        }

        return setContentId(stack, definition.id());
    }

    @Override
    public Optional<ContentID> getContentId(ItemStack stack) {
        if (stack == null) {
            return Optional.empty();
        }

        String value = LegacyNbtIdentity.read(stack);
        if (value == null) {
            return Optional.empty();
        }

        return Optional.of(ContentID.parse(value, "voxelhorizons"));
    }

    @Override
    public ItemStack setContentId(ItemStack stack, ContentID id) {
        if (stack == null) {
            throw new IllegalArgumentException("stack cannot be null");
        }
        return LegacyNbtIdentity.write(stack, id.toString());
    }
}
