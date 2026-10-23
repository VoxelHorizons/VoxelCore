package org.voxelhorizons.text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Method;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.Test;

/**
 * Paper 26.3 regression tests for titles containing VoxelCore placeholders and
 * independently styled DeluxeMenus font runs. The VoxelCore listener must keep
 * the Adventure component instead of flattening it to a legacy inventory title.
 */
public class InventoryTitleAdventureTest {
    private static Component resolve(Component original, Function<String, String> aliases) throws Exception {
        Class<?> listener = InventoryTitlePlaceholderListener.class;
        Method findBridge = listener.getDeclaredMethod("findPaperTitleOverrideSupport");
        findBridge.setAccessible(true);
        Object paper = findBridge.invoke(null);
        if (paper == null) throw new AssertionError("Paper 26.3 must support rich inventory titles");

        Method resolve = listener.getDeclaredMethod(
                "resolveComponentTitle", Object.class, paper.getClass(), Function.class);
        resolve.setAccessible(true);
        return (Component) resolve.invoke(null, original, paper, aliases);
    }

    @Test
    public void uiBackgroundExpansionDoesNotResetShopFontOnSiblingText() throws Exception {
        Component original = Component.text()
                .append(Component.text(":shop_background:").font(Key.key("minecraft:default")))
                .append(Component.text("Shop Name").color(NamedTextColor.GOLD)
                        .font(Key.key("voxel:shop")))
                .build();

        Component rendered = resolve(original, text ->
                text.replace(":shop_background:", "\u00a7f\ue060\uf800"));

        assertEquals(2, rendered.children().size());
        Component background = rendered.children().get(0);
        Component shopTitle = rendered.children().get(1);
        assertEquals(Key.key("minecraft:default"), background.font());
        assertFalse(background.children().isEmpty());
        assertEquals(Key.key("voxel:shop"), shopTitle.font());
        assertEquals(NamedTextColor.GOLD, shopTitle.color());
        assertEquals("Shop Name", ((TextComponent) shopTitle).content());
    }

    @Test
    public void placeholderExpansionInShopTextKeepsItsOwnFont() throws Exception {
        Component original = Component.text(":offset_8:Shop")
                .font(Key.key("voxel:shop"));
        Component rendered = resolve(original, text ->
                text.replace(":offset_8:", "\uf800"));

        assertEquals(Key.key("voxel:shop"), rendered.font());
        assertEquals("", ((TextComponent) rendered).content());
        assertFalse(rendered.children().isEmpty());
    }

    @Test
    public void unmodifiedTitleIsPassedThroughUnchanged() throws Exception {
        Component original = Component.text("Shop")
                .font(Key.key("voxel:shop"));
        assertSame(original, resolve(original, text -> text));
    }
}
