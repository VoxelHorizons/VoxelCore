package org.voxelhorizons.integration.shopgui;

import net.brcdev.shopgui.economy.EconomyManager;
import net.brcdev.shopgui.economy.EconomyType;
import net.brcdev.shopgui.provider.economy.EconomyProvider;
import org.bukkit.entity.Player;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ShopGuiEconomyPlaceholderProcessorTest {

    @Test
    public void resolvesLiveCurrencyAffixesOncePerProvider() {
        TestEconomyProvider provider = new TestEconomyProvider();
        provider.setCurrencyPrefix(":shop_prefix:");
        provider.setCurrencySuffix(":shop_coin:");

        EconomyManager manager = new TestEconomyManager(provider);
        int changed = ShopGuiEconomyPlaceholderProcessor.process(manager,
                new ShopGuiConfigurationPlaceholderProcessor.Resolver() {
                    @Override public String resolve(String input) {
                        return input.replace(":shop_prefix:", "\uE101")
                                .replace(":shop_coin:", "\uE102");
                    }
                });

        assertEquals(2, changed);
        assertEquals("\uE101", provider.getCurrencyPrefix());
        assertEquals("\uE102", provider.getCurrencySuffix());
    }

    private static final class TestEconomyManager extends EconomyManager {
        private final EconomyProvider provider;

        TestEconomyManager(EconomyProvider provider) {
            this.provider = provider;
        }

        @Override public EconomyProvider getDefaultEconomyProvider() {
            return provider;
        }

        @Override public EconomyProvider getEconomyProvider(EconomyType economyType) {
            return provider;
        }
    }

    private static final class TestEconomyProvider extends EconomyProvider {
        @Override public String getName() { return "test"; }
        @Override public double getBalance(Player player) { return 0; }
        @Override public void deposit(Player player, double amount) { }
        @Override public void withdraw(Player player, double amount) { }
    }
}
