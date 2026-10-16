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
            // presentation-only GUI icons. Allow abstract render variants ONLY in
            // known display-only config paths. Tradeable shop stock stays concrete.
            return isGuiButtonItem(section.getCurrentPath())
                    ? items.createRender(id)
                    : items.create(id);
        } catch (IllegalArgumentException exception) {
            String path = section.getCurrentPath();
            String message = "ShopGUI+ references invalid VoxelCore item '" + configuredId
                    + "' at " + path + ": " + exception.getMessage()
                    + (isGuiButtonItem(path) ? " (GUI icons require a renderable model and material)"
                        : " (shop stock requires a non-abstract item definition)");
            logger.log(Level.SEVERE, message);
            // Returning null here allows ShopGUI+ to retain an incomplete GuiButton and crash later
            // in AmountSelectionGui. Fail at the authored configuration instead.
            throw new IllegalArgumentException(message, exception);
        }
    }

    /**
     * ShopGUI+ calls ItemProvider for both tradeable products and decorative
     * button/selector icons. Their config paths distinguish the two usages:
     *
     * buttons.goBack.item                                  (global navigation)
     * amountSelectionGUI.buttons.set1.item                (quantity selector)
     * amountSelectionGUIBulkBuy.buttons.buy1.item         (other GUI variants)
     * shopMenuItems.1.item                                 (shop selector icon)
     *
     * The nested GUI root is deliberately matched by its "GUI" component,
     * rather than an exhaustive hardcoded list of button names or menus. This
     * covers ShopGUI+ GUI variants but NEVER arbitrary shops.* product paths.
     */
    static boolean isGuiButtonItem(String path) {
        if (path == null) return false;
        String[] parts = path.split("\\.", -1);
        if (parts.length == 3 && "item".equalsIgnoreCase(parts[2])) {
            // Only well-known display-only selector/button sections.
            return !parts[1].isEmpty() && ("buttons".equalsIgnoreCase(parts[0])
                    || "shopMenuItems".equalsIgnoreCase(parts[0]));
        }
        return parts.length == 4
                && parts[0].toLowerCase(java.util.Locale.ROOT).contains("gui")
                && "buttons".equalsIgnoreCase(parts[1])
                && !parts[2].isEmpty()
                && "item".equalsIgnoreCase(parts[3]);
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
