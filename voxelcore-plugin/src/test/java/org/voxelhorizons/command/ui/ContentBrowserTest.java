package org.voxelhorizons.command.ui;

import org.junit.Test;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class ContentBrowserTest {

    @Test
    public void reservesBottomRowForControls() {
        assertEquals(45, ContentBrowser.CONTENT_SLOTS);
        assertEquals(54, ContentBrowser.INVENTORY_SIZE);
        assertEquals(45, ContentBrowser.PREVIOUS_SLOT);
        assertEquals(49, ContentBrowser.CLOSE_SLOT);
        assertEquals(53, ContentBrowser.NEXT_SLOT);
    }

    @Test
    public void calculatesPagesFromFortyFiveContentSlots() {
        assertEquals(1, ContentBrowser.pageCount(0));
        assertEquals(1, ContentBrowser.pageCount(1));
        assertEquals(1, ContentBrowser.pageCount(45));
        assertEquals(2, ContentBrowser.pageCount(46));
        assertEquals(2, ContentBrowser.pageCount(90));
        assertEquals(3, ContentBrowser.pageCount(91));
    }
    @Test
    public void retainsPreviousTitlesWithoutConfiguredBrowserKeys() {
        YamlConfiguration config = new YamlConfiguration();
        assertEquals("VC Items 1/3", ContentBrowser.configuredTitle(
                config, ContentBrowser.Type.ITEMS, 0, 3, 110, Function.identity()));
        assertEquals("VC Blocks 3/3", ContentBrowser.configuredTitle(
                config, ContentBrowser.Type.BLOCKS, 2, 3, 110, Function.identity()));
    }

    @Test
    public void itemAndBlockTitlesHaveSeparateConfigTemplates() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("ui.browser.titles.items", "&6Items {page} of {pages} ({total})");
        config.set("ui.browser.titles.blocks", "&b{type} {page}/{pages}");
        assertEquals("§6Items 2 of 4 (170)", ContentBrowser.configuredTitle(
                config, ContentBrowser.Type.ITEMS, 1, 4, 170, Function.identity()));
        assertEquals("§bBlocks 1/2", ContentBrowser.configuredTitle(
                config, ContentBrowser.Type.BLOCKS, 0, 2, 49, Function.identity()));
    }

    @Test
    public void interpolatesPageBeforePassingGlyphToVoxelCoreResolver() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("ui.browser.titles.items", "&f:offset_-8::vc_items_page_{page}:");
        final AtomicReference<String> received = new AtomicReference<String>();
        String title = ContentBrowser.configuredTitle(
                config, ContentBrowser.Type.ITEMS, 1, 5, 190,
                value -> {
                    received.set(value);
                    return value.replace(":offset_-8:", "\uF800")
                            .replace(":vc_items_page_2:", "\uE002");
                });
        assertEquals("&f:offset_-8::vc_items_page_2:", received.get());
        assertEquals("§f\uF800\uE002", title);
    }

    @Test
    public void exactPageOverridesHavePriorityAndOthersFallBack() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("ui.browser.titles.items", "Items template {page}/{pages}");
        config.set("ui.browser.titles.blocks", "Blocks template {page}/{pages}");
        config.set("ui.browser.page_titles.items.1", "&f:custom_first_page:");
        config.set("ui.browser.page_titles.items.3", "&6Third of {pages}");
        config.set("ui.browser.page_titles.blocks.2", "&b:custom_second_block_page:");
        assertEquals("§f:custom_first_page:", ContentBrowser.configuredTitle(
                config, ContentBrowser.Type.ITEMS, 0, 4, 180, Function.identity()));
        assertEquals("Items template 2/4", ContentBrowser.configuredTitle(
                config, ContentBrowser.Type.ITEMS, 1, 4, 180, Function.identity()));
        assertEquals("§6Third of 4", ContentBrowser.configuredTitle(
                config, ContentBrowser.Type.ITEMS, 2, 4, 180, Function.identity()));
        assertEquals("Blocks template 1/3", ContentBrowser.configuredTitle(
                config, ContentBrowser.Type.BLOCKS, 0, 3, 120, Function.identity()));
        assertEquals("§b:custom_second_block_page:", ContentBrowser.configuredTitle(
                config, ContentBrowser.Type.BLOCKS, 1, 3, 120, Function.identity()));
    }

    @Test
    public void intentionallyBlankPageTitleIsAllowedForCustomBackground() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("ui.browser.page_titles.items.1", "");
        assertEquals("", ContentBrowser.configuredTitle(
                config, ContentBrowser.Type.ITEMS, 0, 1, 1, Function.identity()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void preventsInvalidPageIndices() {
        ContentBrowser.configuredTitle(new YamlConfiguration(), ContentBrowser.Type.ITEMS,
                3, 2, 45, Function.identity());
    }

}
