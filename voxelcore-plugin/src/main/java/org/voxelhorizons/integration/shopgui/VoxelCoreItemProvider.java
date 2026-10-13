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
            ContentID id = parseReference(configuredId);
            // ShopGUI+ uses the same item provider for both real shop stock and
            // presentation-only buttons. Allow abstract render variants ONLY in
            // settings GUI button slots. Normal shop entries must remain concrete.
            return isGuiButtonItem(section.getCurrentPath())
                    ? items.createRender(id)
                    : items.create(id);
        } catch (IllegalArgumentException exception) {
            String path = section.getCurrentPath();
            String message = "ShopGUI+ references invalid VoxelCore item '" + configuredId
                    + "' at " + path + ": " + exception.getMessage()
                    + (isGuiButtonItem(path) ? " (GUI buttons require a renderable model and material)"
                        : " (shop stock requires a non-abstract item definition)");
            logger.log(Level.SEVERE, message);
            // Returning null here allows ShopGUI+ to retain an incomplete GuiButton and crash later
            // in AmountSelectionGui. Fail at the authored configuration instead.
            throw new IllegalArgumentException(message, exception);
        }
    }

    /**
     * ShopGUI+ global settings contain decorative buttons at e.g.
     * buttons.goBack.item. These are displays, not buyable shop contents.
     * Do NOT allow this path to match arbitrary shop entries by suffix:
     * shops.<name>.items.<id>.item always uses createItem.
     */
    static boolean isGuiButtonItem(String path) {
        if (path == null) return false;
        String[] parts = path.split("\\.", -1);
        return parts.length == 3 && "buttons".equalsIgnoreCase(parts[0])
                && !parts[1].isEmpty() && "item".equalsIgnoreCase(parts[2]);
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
