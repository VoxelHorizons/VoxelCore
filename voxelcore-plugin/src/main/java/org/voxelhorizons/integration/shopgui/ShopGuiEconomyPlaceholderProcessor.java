package org.voxelhorizons.integration.shopgui;

import net.brcdev.shopgui.ShopGuiPlugin;
import net.brcdev.shopgui.economy.EconomyManager;
import net.brcdev.shopgui.economy.EconomyType;
import net.brcdev.shopgui.provider.economy.EconomyProvider;
import org.voxelhorizons.text.TextPlaceholderService;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Resolves placeholders copied by ShopGUI+ from YAML into its live economy providers. */
final class ShopGuiEconomyPlaceholderProcessor {

    private ShopGuiEconomyPlaceholderProcessor() {
    }

    static int process(ShopGuiPlugin shopGui, TextPlaceholderService placeholders) {
        if (shopGui == null || placeholders == null) return 0;

        try {
            return process(shopGui.getEconomyManager(), new ShopGuiConfigurationPlaceholderProcessor.Resolver() {
                @Override public String resolve(String input) {
                    return placeholders.resolve(input);
                }
            });
        } catch (RuntimeException ignored) {
            return 0;
        } catch (LinkageError ignored) {
            // Keep the optional integration safe if a future ShopGUI+ release changes this API.
            return 0;
        }
    }

    static int process(EconomyManager economies, ShopGuiConfigurationPlaceholderProcessor.Resolver resolver) {
        if (economies == null || resolver == null) return 0;

        Set<EconomyProvider> processed = Collections.newSetFromMap(
                new IdentityHashMap<EconomyProvider, Boolean>());
        int changed = process(economies.getDefaultEconomyProvider(), resolver, processed);
        for (EconomyType type : EconomyType.values()) {
            changed += process(economies.getEconomyProvider(type), resolver, processed);
        }
        return changed;
    }

    private static int process(EconomyProvider provider,
                               ShopGuiConfigurationPlaceholderProcessor.Resolver resolver,
                               Set<EconomyProvider> processed) {
        if (provider == null || !processed.add(provider)) return 0;

        int changed = 0;
        String prefix = provider.getCurrencyPrefix();
        String resolvedPrefix = resolver.resolve(prefix);
        if (!same(prefix, resolvedPrefix)) {
            provider.setCurrencyPrefix(resolvedPrefix);
            changed++;
        }

        String suffix = provider.getCurrencySuffix();
        String resolvedSuffix = resolver.resolve(suffix);
        if (!same(suffix, resolvedSuffix)) {
            provider.setCurrencySuffix(resolvedSuffix);
            changed++;
        }
        return changed;
    }

    private static boolean same(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }
}
