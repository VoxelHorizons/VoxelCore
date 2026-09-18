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

    @Test
    public void ignoresApiGettersMissingFromInstalledShopGuiVersion() {
        assertEquals(null, ShopGuiConfigurationPlaceholderProcessor.config(
                new ShopGuiWithoutAggregateConfig(), "getConfigShops"));
    }

    @Test
    public void readsConfigurationFromNonPublicRuntimeWrapper() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("message", ":shop_prefix: reloaded");
        FileWrapperShopGui shopGui = new FileWrapperShopGui(config);

        assertEquals(config, ShopGuiConfigurationPlaceholderProcessor.config(
                shopGui, "getConfigLang"));
        assertEquals(1, ShopGuiConfigurationPlaceholderProcessor.process(config, placeholders()));
        assertEquals("\uE101 reloaded", config.getString("message"));
    }

    public static final class ShopGuiWithoutAggregateConfig {
        public Object getConfigMain() {
            return null;
        }
    }

    public static final class FileWrapperShopGui {
        private final HiddenConfigWrapper wrapper;

        FileWrapperShopGui(YamlConfiguration config) {
            this.wrapper = new HiddenConfigWrapper(config);
        }

        public Object getConfigLang() {
            return wrapper;
        }
    }

    private static final class HiddenConfigWrapper {
        private final YamlConfiguration config;

        HiddenConfigWrapper(YamlConfiguration config) {
            this.config = config;
        }

        public YamlConfiguration getConfig() {
            return config;
        }
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
