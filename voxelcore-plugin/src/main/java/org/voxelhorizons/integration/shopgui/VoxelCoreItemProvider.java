package org.voxelhorizons.integration.shopgui;

import net.brcdev.shopgui.provider.item.ItemProvider;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.content.ContentID;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

final class VoxelCoreItemProvider extends ItemProvider {

    static final String CONFIG_KEY = "voxelcore";

    private final VoxelCoreItemResolver items;
    private final Logger logger;

    VoxelCoreItemProvider(VoxelCoreItemResolver items, Logger logger) {
        super("VoxelCore");
        this.items = items;
        this.logger = logger;
    }

    @Override
    public boolean isValidItem(ItemStack stack) {
        return stack != null && items.identify(stack).isPresent();
    }

    @Override
    public ItemStack loadItem(ConfigurationSection section) {
        String configuredId = section.getString(CONFIG_KEY);
        if (configuredId == null || configuredId.trim().isEmpty()) return null;

        try {
            return items.create(parseReference(configuredId));
        } catch (IllegalArgumentException exception) {
            String message = "ShopGUI+ references invalid VoxelCore item '" + configuredId
                    + "' at " + section.getCurrentPath() + ": " + exception.getMessage();
            logger.log(Level.SEVERE, message);
            // Returning null here allows ShopGUI+ to retain an incomplete GuiButton and crash later
            // in AmountSelectionGui. Fail at the authored configuration instead.
            throw new IllegalArgumentException(message, exception);
        }
    }

    static ContentID parseReference(String configuredId) {
        String input = configuredId == null ? "" : configuredId.trim();
        try {
            return ContentID.parse(input, "voxel");
        } catch (IllegalArgumentException exception) {
            if (input.indexOf('/') < 0) throw exception;
            // VoxelCore item keys use hyphens (ui-blank), while resource/model paths commonly use
            // slashes (ui/blank). Accept the resource-style spelling as an integration shorthand.
            return ContentID.parse(input.replace('/', '-'), "voxel");
        }
    }

    @Override
    public boolean compare(ItemStack first, ItemStack second) {
        if (first == null || second == null) return false;
        Optional<ContentID> firstId = items.identify(first);
        return firstId.isPresent() && firstId.equals(items.identify(second));
    }
}
