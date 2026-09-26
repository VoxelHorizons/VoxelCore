package org.voxelhorizons.platform.v26_2;

import org.bukkit.entity.Player;
import org.voxelhorizons.platform.network.PacketChannelAdapter;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class v26_2_PacketChannelAdapter implements PacketChannelAdapter {
    private static final String HANDLER_PREFIX = "voxelcore_packet_";

    private final Map<UUID, Object> channels = new ConcurrentHashMap<>();

    @Override
    public boolean supported() {
        try {
            Class.forName("io.netty.channel.ChannelInboundHandler");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    @Override
    public void inject(Player player, PacketInterceptor interceptor) {
        if (!supported() || player == null || interceptor == null) return;

        try {
            Object channel = resolveChannel(player);
            if (channel == null) return;

            Object pipeline = invokeNoArgs(channel, "pipeline");
            if (pipeline == null) return;

            String handlerName = HANDLER_PREFIX + player.getUniqueId().toString().replace("-", "");
            if (pipelineGet(pipeline, handlerName) != null) {
                channels.put(player.getUniqueId(), channel);
                return;
            }

            ClassLoader loader = channel.getClass().getClassLoader();
            Class<?> inboundHandler = Class.forName("io.netty.channel.ChannelInboundHandler", true, loader);
            Object proxy = Proxy.newProxyInstance(loader, new Class<?>[]{inboundHandler},
                    new InboundHandlerInvocation(player, interceptor));

            String anchor = findPacketHandlerName(pipeline);
            if (anchor != null) {
                invokePipeline(pipeline, "addBefore", anchor, handlerName, proxy);
            } else {
                invokePipelineAddLast(pipeline, handlerName, proxy);
            }

            channels.put(player.getUniqueId(), channel);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fail closed. Unsupported/changed internals must never disrupt a player's connection.
        }
    }

    @Override
    public void uninject(Player player) {
        if (player == null) return;
        Object channel = channels.remove(player.getUniqueId());
        if (channel == null) {
            try {
                channel = resolveChannel(player);
            } catch (ReflectiveOperationException ignored) {
                return;
            }
        }
        if (channel == null) return;

        try {
            Object pipeline = invokeNoArgs(channel, "pipeline");
            if (pipeline == null) return;
            String handlerName = HANDLER_PREFIX + player.getUniqueId().toString().replace("-", "");
            if (pipelineGet(pipeline, handlerName) != null) {
                Method remove = findMethod(pipeline.getClass(), "remove", String.class);
                if (remove != null) remove.invoke(pipeline, handlerName);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Player is leaving / server is shutting down; nothing else to do.
        }
    }

    private static Object resolveChannel(Player player) throws ReflectiveOperationException {
        Method getHandle = player.getClass().getMethod("getHandle");
        Object handle = getHandle.invoke(player);
        if (handle == null) return null;

        Object packetListener = findFieldValue(handle, "ServerGamePacketListenerImpl");
        if (packetListener == null) return null;

        Object connection = findFieldValue(packetListener, "Connection");
        if (connection == null) return null;

        return findAssignableFieldValue(connection, "io.netty.channel.Channel");
    }

    private static Object findFieldValue(Object instance, String simpleTypeName) {
        Class<?> type = instance.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                Class<?> fieldType = field.getType();
                if (!fieldType.getSimpleName().equals(simpleTypeName)
                        && !fieldType.getName().endsWith("." + simpleTypeName)) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(instance);
                    if (value != null) return value;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static Object findAssignableFieldValue(Object instance, String className) {
        Class<?> wanted;
        try {
            wanted = Class.forName(className, false, instance.getClass().getClassLoader());
        } catch (ClassNotFoundException exception) {
            return null;
        }

        Class<?> type = instance.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (!wanted.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(instance);
                    if (value != null) return value;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static Object pipelineGet(Object pipeline, String name) throws ReflectiveOperationException {
        Method get = findMethod(pipeline.getClass(), "get", String.class);
        return get == null ? null : get.invoke(pipeline, name);
    }

    @SuppressWarnings("unchecked")
    private static String findPacketHandlerName(Object pipeline) throws ReflectiveOperationException {
        Method names = findMethod(pipeline.getClass(), "names");
        if (names == null) return null;
        Object result = names.invoke(pipeline);
        if (!(result instanceof List)) return null;

        for (Object raw : (List<Object>) result) {
            if (!(raw instanceof String)) continue;
            String name = (String) raw;
            String normalized = name.toLowerCase(Locale.ROOT);
            if (normalized.equals("packet_handler") || normalized.contains("packet_handler")) return name;
        }
        return null;
    }

    private static void invokePipeline(Object pipeline, String methodName,
                                       String baseName, String handlerName, Object handler)
            throws ReflectiveOperationException {
        for (Method method : pipeline.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 3) continue;
            method.invoke(pipeline, baseName, handlerName, handler);
            return;
        }
        throw new NoSuchMethodException(methodName);
    }

    private static void invokePipelineAddLast(Object pipeline, String handlerName, Object handler)
            throws ReflectiveOperationException {
        for (Method method : pipeline.getClass().getMethods()) {
            if (!method.getName().equals("addLast")) continue;
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 2 && parameterTypes[0] == String.class) {
                method.invoke(pipeline, handlerName, handler);
                return;
            }
        }
        throw new NoSuchMethodException("addLast");
    }

    private static Object invokeNoArgs(Object target, String method) throws ReflectiveOperationException {
        Method found = findMethod(target.getClass(), method);
        return found == null ? null : found.invoke(target);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getMethod(name, parameters);
        } catch (NoSuchMethodException ignored) {
            for (Method method : type.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameters.length) return method;
            }
            return null;
        }
    }

    private static final class InboundHandlerInvocation implements InvocationHandler {
        private final Player player;
        private final PacketInterceptor interceptor;

        private InboundHandlerInvocation(Player player, PacketInterceptor interceptor) {
            this.player = player;
            this.interceptor = interceptor;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();

            if ("channelRead".equals(name) && args != null && args.length == 2) {
                Object context = args[0];
                Object packet = args[1];
                if (!interceptor.onInboundPacket(player, packet)) {
                    invokeContext(context, "fireChannelRead", packet);
                }
                return null;
            }

            if ("exceptionCaught".equals(name) && args != null && args.length == 2) {
                invokeContext(args[0], "fireExceptionCaught", args[1]);
                return null;
            }

            if (args != null && args.length >= 1 && name.startsWith("channel")) {
                String fireName = "fire" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
                Object[] forwarded = new Object[Math.max(0, args.length - 1)];
                if (forwarded.length > 0) System.arraycopy(args, 1, forwarded, 0, forwarded.length);
                invokeContext(args[0], fireName, forwarded);
                return null;
            }

            if ("userEventTriggered".equals(name) && args != null && args.length == 2) {
                invokeContext(args[0], "fireUserEventTriggered", args[1]);
                return null;
            }

            // handlerAdded / handlerRemoved and Object methods need no forwarding.
            if ("toString".equals(name)) return "VoxelCorePacketInterceptor(" + player.getName() + ")";
            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
            if ("equals".equals(name)) return proxy == (args == null ? null : args[0]);
            return null;
        }

        private static void invokeContext(Object context, String methodName, Object... values) {
            if (context == null) return;
            for (Method method : context.getClass().getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != values.length) continue;
                try {
                    method.invoke(context, values);
                    return;
                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                }
            }
        }
    }
}
