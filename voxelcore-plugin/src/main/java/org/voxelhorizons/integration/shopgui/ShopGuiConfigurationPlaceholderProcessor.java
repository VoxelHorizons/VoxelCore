package org.voxelhorizons.integration.shopgui;

import net.brcdev.shopgui.ShopGuiPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.voxelhorizons.text.TextPlaceholderService;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ShopGuiConfigurationPlaceholderProcessor {

    private ShopGuiConfigurationPlaceholderProcessor() {
    }

    static int process(ShopGuiPlugin shopGui, TextPlaceholderService placeholders) {
        if (shopGui == null || placeholders == null) return 0;

        Resolver resolver = new Resolver() {
            @Override public String resolve(String input) {
                return placeholders.resolve(input);
            }
        };

        int changed = 0;
        changed += process(config(shopGui, "getConfigMain"), resolver);
        changed += process(config(shopGui, "getConfigLang"), resolver);
        changed += process(config(shopGui, "getConfigPriceModifiers"), resolver);
        changed += process(config(shopGui, "getConfigShops"), resolver);
        return changed;
    }

    static int process(FileConfiguration config, Resolver resolver) {
        if (config == null || resolver == null) return 0;

        int changed = 0;
        // Copy first because ConfigurationSection#set mutates the backing map.
        Map<String, Object> values = new LinkedHashMap<String, Object>(config.getValues(true));
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getValue() instanceof ConfigurationSection) continue;
            Object resolved = resolve(entry.getValue(), resolver);
            if (!same(entry.getValue(), resolved)) {
                config.set(entry.getKey(), resolved);
                changed++;
            }
        }
        return changed;
    }

    /**
     * ShopGUI+'s published API has exposed getters that are absent from some matching plugin
     * releases (notably getConfigShops in 1.113.0). Discover both getter levels at runtime so an
     * optional configuration domain can never fail the ShopGUI+ load event with NoSuchMethodError.
     */
    static FileConfiguration config(Object shopGui, String getterName) {
        if (shopGui == null) return null;
        try {
            Method getter = shopGui.getClass().getMethod(getterName);
            if (!getter.isAccessible()) getter.setAccessible(true);
            Object wrapper = getter.invoke(shopGui);
            if (wrapper == null) return null;
            Method getConfig = wrapper.getClass().getMethod("getConfig");
            if (!getConfig.isAccessible()) getConfig.setAccessible(true);
            Object config = getConfig.invoke(wrapper);
            return config instanceof FileConfiguration ? (FileConfiguration) config : null;
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (IllegalAccessException ignored) {
            return null;
        } catch (InvocationTargetException ignored) {
            return null;
        } catch (LinkageError ignored) {
            return null;
        }
    }

    private static Object resolve(Object value, Resolver resolver) {
        if (value instanceof String) {
            return resolver.resolve((String) value);
        }
        if (value instanceof List<?>) {
            List<?> source = (List<?>) value;
            List<Object> resolved = new ArrayList<Object>(source.size());
            for (Object element : source) resolved.add(resolve(element, resolver));
            return resolved;
        }
        if (value instanceof Map<?, ?>) {
            Map<?, ?> source = (Map<?, ?>) value;
            Map<Object, Object> resolved = new LinkedHashMap<Object, Object>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                resolved.put(entry.getKey(), resolve(entry.getValue(), resolver));
            }
            return resolved;
        }
        return value;
    }

    private static boolean same(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    interface Resolver {
        String resolve(String input);
    }
}
