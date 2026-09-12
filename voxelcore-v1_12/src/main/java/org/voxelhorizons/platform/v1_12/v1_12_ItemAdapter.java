package org.voxelhorizons.platform.v1_12;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.CustomModelDataDefinition;
import org.voxelhorizons.content.item.ItemDefinition;
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
        Integer legacyDamage = null;

        if (meta != null) {
            if (definition.displayName() != null) meta.setDisplayName(definition.displayName());
            if (!definition.lore().isEmpty()) meta.setLore(definition.lore());

            if (definition.render() != null && definition.render().customModelData() != null) {
                CustomModelDataDefinition customModelData = definition.render().customModelData();
                if (!customModelData.isNumeric()) {
                    throw new IllegalArgumentException("Structured custom_model_data requires Minecraft 1.21.4+ for " + definition.id());
                }
                int damage = customModelData.numeric().intValue();
                int maxDurability = material.getMaxDurability();
                if (maxDurability <= 0) {
                    throw new IllegalArgumentException("Minecraft 1.12-1.13 custom models require a damageable material for " + definition.id());
                }
                if (damage <= 0 || damage >= maxDurability) {
                    throw new IllegalArgumentException("custom_model_data " + damage + " cannot be used as legacy damage for "
                            + definition.id() + "; expected 1.." + (maxDurability - 1));
                }
                meta.setUnbreakable(true);
                legacyDamage = Integer.valueOf(damage);
            }
            stack.setItemMeta(meta);
        }

        // ItemStack#setDurability must be applied after ItemMeta on legacy Bukkit or
        // a later setItemMeta call can overwrite the damage value.
        if (legacyDamage != null) stack.setDurability(legacyDamage.shortValue());
        return setContentId(stack, definition.id());
    }

    @Override
    public Optional<ContentID> getContentId(ItemStack stack) {
        if (stack == null) return Optional.empty();
        String value = LegacyNbtIdentity.read(stack);
        return value == null ? Optional.<ContentID>empty() : Optional.of(ContentID.parse(value, "voxelhorizons"));
    }

    @Override
    public ItemStack setContentId(ItemStack stack, ContentID id) {
        if (stack == null) throw new IllegalArgumentException("stack cannot be null");
        return LegacyNbtIdentity.write(stack, id.toString());
    }
}
