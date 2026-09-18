package org.voxelhorizons.integration.shopgui;

import org.bukkit.inventory.meta.ItemMeta;
import org.voxelhorizons.text.TextPlaceholderService;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Handles modern Paper item components without linking Adventure into the 1.12-compatible jar. */
final class AdventureItemMetaPlaceholderProcessor {

    private static final Bridge BRIDGE = Bridge.create();

    private AdventureItemMetaPlaceholderProcessor() {
    }

    static boolean process(ItemMeta meta, TextPlaceholderService placeholders) {
        return BRIDGE != null && BRIDGE.process(meta, placeholders);
    }

    private static final class Bridge {
        private final Method displayNameGetter;
        private final Method displayNameSetter;
        private final Method loreGetter;
        private final Method loreSetter;
        private final Object serializer;
        private final Method serialize;
        private final Method deserialize;

        private Bridge(Method displayNameGetter, Method displayNameSetter,
                       Method loreGetter, Method loreSetter, Object serializer,
                       Method serialize, Method deserialize) {
            this.displayNameGetter = displayNameGetter;
            this.displayNameSetter = displayNameSetter;
            this.loreGetter = loreGetter;
            this.loreSetter = loreSetter;
            this.serializer = serializer;
            this.serialize = serialize;
            this.deserialize = deserialize;
        }

        static Bridge create() {
            try {
                Class<?> component = Class.forName("net.kyori.adventure.text.Component");
                Class<?> serializerType = Class.forName(
                        "net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
                Object serializer = serializerType.getMethod("legacySection").invoke(null);
                return new Bridge(
                        ItemMeta.class.getMethod("displayName"),
                        ItemMeta.class.getMethod("displayName", component),
                        ItemMeta.class.getMethod("lore"),
                        ItemMeta.class.getMethod("lore", List.class),
                        serializer,
                        serializerType.getMethod("serialize", component),
                        serializerType.getMethod("deserialize", String.class));
            } catch (ClassNotFoundException ignored) {
                return null;
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

        boolean process(ItemMeta meta, TextPlaceholderService placeholders) {
            try {
                boolean changed = false;
                Object displayName = displayNameGetter.invoke(meta);
                Object resolvedName = resolve(displayName, placeholders);
                if (displayName != resolvedName) {
                    displayNameSetter.invoke(meta, resolvedName);
                    changed = true;
                }

                Object rawLore = loreGetter.invoke(meta);
                if (rawLore instanceof List<?>) {
                    List<?> lore = (List<?>) rawLore;
                    List<Object> resolvedLore = new ArrayList<Object>(lore.size());
                    boolean loreChanged = false;
                    for (Object line : lore) {
                        Object resolvedLine = resolve(line, placeholders);
                        resolvedLore.add(resolvedLine);
                        loreChanged |= line != resolvedLine;
                    }
                    if (loreChanged) {
                        loreSetter.invoke(meta, resolvedLore);
                        changed = true;
                    }
                }
                return changed;
            } catch (IllegalAccessException ignored) {
                return false;
            } catch (InvocationTargetException ignored) {
                return false;
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }

        private Object resolve(Object component, TextPlaceholderService placeholders)
                throws InvocationTargetException, IllegalAccessException {
            if (component == null) return null;
            String current = (String) serialize.invoke(serializer, component);
            String resolved = placeholders.resolve(current);
            if (current.equals(resolved)) return component;
            return deserialize.invoke(serializer, resolved);
        }
    }
}
