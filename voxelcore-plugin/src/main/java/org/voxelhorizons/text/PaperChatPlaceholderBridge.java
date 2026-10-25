package org.voxelhorizons.text;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

/**
 * Reflection-only Paper chat bridge so the shared plugin module can keep compiling against Bukkit
 * 1.12 while still integrating with Paper's modern AsyncChatEvent/Adventure renderer pipeline.
 */
public final class PaperChatPlaceholderBridge {
    private final Plugin plugin;
    private final TextPlaceholderService placeholders;
    private final Class<? extends Event> asyncChatEventClass;
    private final Class<?> chatRendererClass;
    private final Object legacySerializer;
    private final Method eventPlayer;
    private final Method eventMessageGet;
    private final Method eventMessageSet;
    private final Method eventRendererGet;
    private final Method eventRendererSet;
    private final Method serialize;
    private final Method deserialize;
    private boolean warned;

    @SuppressWarnings("unchecked")
    private PaperChatPlaceholderBridge(Plugin plugin, TextPlaceholderService placeholders) throws ReflectiveOperationException {
        this.plugin = plugin;
        this.placeholders = placeholders;
        this.asyncChatEventClass = (Class<? extends Event>) Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
        this.chatRendererClass = Class.forName("io.papermc.paper.chat.ChatRenderer");
        Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
        Class<?> serializerClass = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
        this.legacySerializer = serializerClass.getMethod("legacySection").invoke(null);
        this.serialize = serializerClass.getMethod("serialize", componentClass);
        this.deserialize = serializerClass.getMethod("deserialize", String.class);
        this.eventPlayer = asyncChatEventClass.getMethod("getPlayer");
        this.eventMessageGet = asyncChatEventClass.getMethod("message");
        this.eventMessageSet = asyncChatEventClass.getMethod("message", componentClass);
        this.eventRendererGet = asyncChatEventClass.getMethod("renderer");
        this.eventRendererSet = asyncChatEventClass.getMethod("renderer", chatRendererClass);
    }

    public static boolean registerIfAvailable(Plugin plugin, TextPlaceholderService placeholders) {
        try {
            PaperChatPlaceholderBridge bridge = new PaperChatPlaceholderBridge(plugin, placeholders);
            bridge.register();
            plugin.getLogger().info("Enabled Paper AsyncChatEvent placeholder integration.");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Unable to enable Paper chat placeholder integration: " + exception.getMessage());
            return false;
        }
    }

    private void register() {
        Listener listener = new Listener() { };
        plugin.getServer().getPluginManager().registerEvent(asyncChatEventClass, listener, EventPriority.HIGH,
                new EventExecutor() {
                    @Override public void execute(Listener ignored, Event event) {
                        resolvePlayerMessage(event);
                    }
                }, plugin, true);
        plugin.getServer().getPluginManager().registerEvent(asyncChatEventClass, listener, EventPriority.MONITOR,
                new EventExecutor() {
                    @Override public void execute(Listener ignored, Event event) {
                        wrapRenderer(event);
                    }
                }, plugin, true);
    }

    private void resolvePlayerMessage(Event event) {
        try {
            Player player = (Player) eventPlayer.invoke(event);
            boolean allowInline = player.hasPermission(ChatPlaceholderListener.INLINE_PERMISSION);
            boolean allowGui = player.hasPermission(ChatPlaceholderListener.GUI_PERMISSION);
            if (!allowInline && !allowGui) return;

            Object component = eventMessageGet.invoke(event);
            String legacy = (String) serialize.invoke(legacySerializer, component);
            String resolved = placeholders.resolve(legacy, allowInline, allowGui);
            if (resolved.equals(legacy)) return;
            eventMessageSet.invoke(event, deserialize.invoke(legacySerializer, resolved));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOnce("Unable to resolve placeholders in Paper player chat message", exception);
        }
    }

    private void wrapRenderer(Event event) {
        try {
            final Player player = (Player) eventPlayer.invoke(event);
            final Object renderer = eventRendererGet.invoke(event);
            if (renderer == null || Proxy.isProxyClass(renderer.getClass())
                    && Proxy.getInvocationHandler(renderer) instanceof PlaceholderRendererHandler) {
                return;
            }

            Object proxy = Proxy.newProxyInstance(plugin.getClass().getClassLoader(), new Class<?>[]{chatRendererClass},
                    new PlaceholderRendererHandler(renderer, player));
            eventRendererSet.invoke(event, proxy);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOnce("Unable to wrap Paper chat renderer for prefix placeholders", exception);
        }
    }

    private final class PlaceholderRendererHandler implements InvocationHandler {
        private final Object delegate;
        private final Player player;

        private PlaceholderRendererHandler(Object delegate, Player player) {
            this.delegate = delegate;
            this.player = player;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (!"render".equals(method.getName())) {
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException exception) {
                    throw exception.getCause();
                }
            }

            Object rendered;
            try {
                rendered = method.invoke(delegate, args);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
            if (rendered == null) return null;

            try {
                String full = (String) serialize.invoke(legacySerializer, rendered);
                String message = args != null && args.length >= 3 && args[2] != null
                        ? (String) serialize.invoke(legacySerializer, args[2]) : null;
                String resolved = resolveServerFormatSafely(full, message, player);
                return resolved.equals(full) ? rendered : deserialize.invoke(legacySerializer, resolved);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                warnOnce("Unable to resolve placeholders in rendered Paper chat format", exception);
                return rendered;
            }
        }
    }

    private String resolveServerFormatSafely(String full, String message, Player player) {
        if (full == null || full.indexOf(':') < 0) return full;
        if (message == null || message.isEmpty()) return placeholders.resolve(full);

        boolean allowInline = player.hasPermission(ChatPlaceholderListener.INLINE_PERMISSION);
        boolean allowGui = player.hasPermission(ChatPlaceholderListener.GUI_PERMISSION);
        String restrictedMessage = placeholders.resolve(message, allowInline, allowGui);
        String unrestrictedMessage = placeholders.resolve(message);
        if (restrictedMessage.equals(unrestrictedMessage)) return placeholders.resolve(full);

        int index = full.lastIndexOf(message);
        if (index < 0) return full;
        String marker = "__VOXELCORE_PLAYER_MESSAGE_" + UUID.randomUUID().toString().replace("-", "") + "__";
        String protectedFormat = full.substring(0, index) + marker + full.substring(index + message.length());
        return placeholders.resolve(protectedFormat).replace(marker, message);
    }

    private void warnOnce(String message, Exception exception) {
        if (warned) return;
        warned = true;
        plugin.getLogger().warning(message + ": " + exception.getMessage());
    }
}
