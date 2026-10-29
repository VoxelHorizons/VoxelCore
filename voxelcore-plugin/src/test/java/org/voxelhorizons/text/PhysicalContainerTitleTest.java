package org.voxelhorizons.text;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.inventory.InventoryHolder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Ensures physical-only title selection works on the shared 1.12 API. */
public class PhysicalContainerTitleTest {
    private static Object chest() {
        return Proxy.newProxyInstance(Chest.class.getClassLoader(), new Class<?>[] { Chest.class },
                (proxy, method, args) -> null);
    }

    private static Object block(Material type) {
        return Proxy.newProxyInstance(BlockState.class.getClassLoader(),
                new Class<?>[] { BlockState.class, InventoryHolder.class },
                (proxy, method, args) -> "getType".equals(method.getName()) ? type : null);
    }

    private static String resolve(Object holder, int size, boolean legacy, String single, String large,
                                  boolean enabled, Map<String, String> templates) {
        return InventoryTitlePlaceholderListener.titleForPhysicalHolder(
                holder, size, legacy, single, large, enabled, templates);
    }

    @Test
    public void retainsLegacySingleAndDoubleChestTitles() {
        Object chest = chest();
        assertEquals("Old single", resolve(chest, 27, true, "Old single", "Old double", false,
                Collections.emptyMap()));
        assertEquals("Old double", resolve(chest, 54, true, "Old single", "Old double", false,
                Collections.emptyMap()));
        assertNull(resolve(chest, 9, true, "Old single", "Old double", false,
                Collections.emptyMap()));
    }

    @Test
    public void newChestTemplatesOverrideLegacyWithoutRemovingFallback() {
        Map<String, String> templates = new HashMap<String, String>();
        templates.put("chest.single", ":single:");
        assertEquals(":single:", resolve(chest(), 27, true, "Old single", "Old double", true, templates));
        assertEquals("Old double", resolve(chest(), 54, true, "Old single", "Old double", true, templates));
        assertEquals("Old single", resolve(chest(), 27, true, "Old single", "Old double", false, templates));
    }

    @Test
    public void materialTitlesOnlyAffectPhysicalHolders() {
        Map<String, String> templates = new HashMap<String, String>();
        templates.put("hopper", ":hopper_ui:");
        assertEquals(":hopper_ui:", resolve(block(Material.HOPPER), 5, false, "", "", true, templates));
        assertNull(resolve(block(Material.HOPPER), 5, false, "", "", false, templates));
        assertNull(resolve(new InventoryHolder() {
            @Override public org.bukkit.inventory.Inventory getInventory() { return null; }
        }, 5, false, "", "", true, templates));
        assertNull(resolve(null, 5, false, "", "", true, templates));
    }

    @Test
    public void shulkerBoxTemplateCanBeSharedOrOverriddenPerColour() {
        Map<String, String> templates = new HashMap<String, String>();
        templates.put("shulker_box", ":shulker_ui:");
        assertEquals(":shulker_ui:", resolve(block(Material.RED_SHULKER_BOX), 27, false,
                "", "", true, templates));
        templates.put("red_shulker_box", ":red_ui:");
        assertEquals(":red_ui:", resolve(block(Material.RED_SHULKER_BOX), 27, false,
                "", "", true, templates));
    }
}
