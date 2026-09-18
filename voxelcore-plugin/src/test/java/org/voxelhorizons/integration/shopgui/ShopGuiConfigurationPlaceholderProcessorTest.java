package org.voxelhorizons.integration.shopgui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ShopGuiConfigurationPlaceholderProcessorTest {

    @Test
    public void resolvesStringsAndNestedCollectionValuesWithoutFlatteningConfiguration() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.prefix", ":shop_prefix: Shop");
        config.set("shop.items.iron.lore", Arrays.asList(
                "&fBuy :shop_coin:", "&7Unchanged"));
        Map<String, Object> nested = new LinkedHashMap<String, Object>();
        nested.put("text", ":shop_prefix: nested");
        config.set("shop.metadata", Collections.singletonList(nested));
        config.set("shop.price", 50);

        int changed = ShopGuiConfigurationPlaceholderProcessor.process(config, placeholders());

        assertEquals(3, changed);
        assertEquals("\uE101 Shop", config.getString("messages.prefix"));
        assertEquals(Arrays.asList("&fBuy \uE102", "&7Unchanged"),
                config.getStringList("shop.items.iron.lore"));
        List<?> metadata = config.getList("shop.metadata");
        assertEquals("\uE101 nested", ((Map<?, ?>) metadata.get(0)).get("text"));
        assertEquals(50, config.getInt("shop.price"));
    }

    @Test
    public void leavesUnknownPlaceholdersReadableForFutureReloads() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("message", ":not_registered:");

        assertEquals(0, ShopGuiConfigurationPlaceholderProcessor.process(config, placeholders()));
        assertEquals(":not_registered:", config.getString("message"));
    }

    private static ShopGuiConfigurationPlaceholderProcessor.Resolver placeholders() {
        return new ShopGuiConfigurationPlaceholderProcessor.Resolver() {
            @Override public String resolve(String input) {
                return input.replace(":shop_prefix:", "\uE101")
                        .replace(":shop_coin:", "\uE102");
            }
        };
    }
}
