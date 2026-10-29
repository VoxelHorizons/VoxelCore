package org.voxelhorizons.text;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.InventoryHolder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Barrel API is available on 26.3, unlike the shared 1.12 API module. */
public class BarrelContainerTitleTest {
    private static Object barrelHolder() {
        return Proxy.newProxyInstance(BlockState.class.getClassLoader(),
                new Class<?>[] { BlockState.class, InventoryHolder.class },
                (proxy, method, args) -> "getType".equals(method.getName()) ? Material.BARREL : null);
    }

    @Test
    public void barrelGetsItsOwnTitleIndependentlyOfChestSettings() {
        Map<String, String> templates = Collections.singletonMap("barrel", "&f:barrel_background:Barrel");
        Object barrel = barrelHolder();
        assertEquals("&f:barrel_background:Barrel",
                InventoryTitlePlaceholderListener.titleForPhysicalHolder(
                        barrel, 27, false, "", "", true, templates));
        assertNull(InventoryTitlePlaceholderListener.titleForPhysicalHolder(
                barrel, 27, true, "Single chest", "Double chest", false, templates));
        assertNull(InventoryTitlePlaceholderListener.titleForPhysicalHolder(
                barrel, 27, false, "", "", true, Collections.emptyMap()));
    }
}
