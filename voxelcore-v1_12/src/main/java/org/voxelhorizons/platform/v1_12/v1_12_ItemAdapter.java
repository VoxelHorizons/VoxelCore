package org.voxelhorizons.platform.v1_12;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.platform.item.ItemMetadataSupport;
import org.voxelhorizons.platform.item.ItemPlatformAdapter;

import java.util.Locale;
import java.util.Optional;

public final class v1_12_ItemAdapter implements ItemPlatformAdapter {

    @Override
    public ItemStack createItem(ItemDefinition definition, int quantity) {
        String materialName = definition.material().replace("minecraft:", "").toUpperCase(Locale.ROOT);
        Material material = Material.matchMaterial(materialName);
        if (material == null) throw new IllegalArgumentException("Unknown Minecraft material: " + definition.material());

        ItemStack stack = new ItemStack(material, quantity);
        ItemMeta meta = stack.getItemMeta();
        Integer durability = null;

        if (meta != null) {
            if (definition.displayName() != null) meta.setDisplayName(definition.displayName());
            if (!definition.lore().isEmpty()) meta.setLore(definition.lore());
            ItemMetadataSupport.applyCommon(meta, definition.render());
            if (definition.render() != null && definition.render().durability() != null) {
                durability = definition.render().durability();
                validateDurability(material, durability.intValue(), definition);
            }
            // custom_model_data intentionally has no runtime representation before 1.14.
            stack.setItemMeta(meta);
        }

        // Legacy Bukkit stores damage/durability directly on ItemStack.
        if (durability != null) stack.setDurability(durability.shortValue());
        return setContentId(stack, definition.id());
    }

    private static void validateDurability(Material material, int durability, ItemDefinition definition) {
        int max = material.getMaxDurability();
        if (max <= 0) {
            throw new IllegalArgumentException("durability requires a damageable material for " + definition.id());
        }
        if (durability < 0 || durability >= max) {
            throw new IllegalArgumentException("durability " + durability + " is invalid for " + definition.id()
                    + "; expected 0.." + (max - 1));
        }
    }

    @Override
    public Optional<ContentID> getContentId(ItemStack stack) {
        if (stack == null) return Optional.empty();
        return ItemPlatformAdapter.parseStoredContentId(LegacyNbtIdentity.read(stack));
    }

    @Override
    public ItemStack setContentId(ItemStack stack, ContentID id) {
        if (stack == null) throw new IllegalArgumentException("stack cannot be null");
        return LegacyNbtIdentity.write(stack, id.toString());
    }
}
