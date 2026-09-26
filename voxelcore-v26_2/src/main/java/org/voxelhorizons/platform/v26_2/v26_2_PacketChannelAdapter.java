package org.voxelhorizons.platform.v26_2;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import org.bukkit.entity.Player;
import org.voxelhorizons.platform.network.PacketChannelAdapter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class v26_2_PacketChannelAdapter implements PacketChannelAdapter {
    private static final String HANDLER_PREFIX = "voxelcore_packet_";

    private final Map<UUID, Channel> channels = new ConcurrentHashMap<>();

    @Override
    public boolean supported() {
        return true;
    }

    @Override
    public void inject(Player player, PacketInterceptor interceptor) {
        if (player == null || interceptor == null) return;

        try {
            Channel channel = resolveChannel(player);
            if (channel == null) return;

            String handlerName = handlerName(player);
            channel.eventLoop().execute(() -> {
                try {
                    ChannelPipeline pipeline = channel.pipeline();
                    if (pipeline.get(handlerName) != null) {
                        channels.put(player.getUniqueId(), channel);
                        return;
                    }

                    ChannelInboundHandlerAdapter handler = new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            boolean consume = false;
                            try {
                                consume = interceptor.onInboundPacket(player, msg);
                            } catch (Throwable throwable) {
                                // Packet interception must never be able to break the player's connection.
                            }

                            if (!consume) {
                                super.channelRead(ctx, msg);
                            }
                        }
                    };

                    if (pipeline.get("packet_handler") != null) {
                        pipeline.addBefore("packet_handler", handlerName, handler);
                    } else {
                        pipeline.addLast(handlerName, handler);
                    }

                    channels.put(player.getUniqueId(), channel);
                } catch (RuntimeException ignored) {
                    // Fail closed. Unsupported/changed internals must never disrupt a player's connection.
                }
            });
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fail closed. Unsupported/changed internals must never disrupt a player's connection.
        }
    }

    @Override
    public void uninject(Player player) {
        if (player == null) return;

        Channel channel = channels.remove(player.getUniqueId());
        if (channel == null) {
            try {
                channel = resolveChannel(player);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return;
            }
        }
        if (channel == null) return;

        final Channel finalChannel = channel;
        final String handlerName = handlerName(player);
        finalChannel.eventLoop().execute(() -> {
            try {
                ChannelPipeline pipeline = finalChannel.pipeline();
                if (pipeline.get(handlerName) != null) {
                    pipeline.remove(handlerName);
                }
            } catch (RuntimeException ignored) {
                // Player may already be disconnected.
            }
        });
    }

    private static String handlerName(Player player) {
        return HANDLER_PREFIX + player.getUniqueId().toString().replace("-", "");
    }

    private static Channel resolveChannel(Player player) throws ReflectiveOperationException {
        Method getHandle = player.getClass().getMethod("getHandle");
        Object handle = getHandle.invoke(player);
        if (handle == null) return null;

        Object packetListener = findFieldValue(handle, "ServerGamePacketListenerImpl");
        if (packetListener == null) return null;

        Object connection = findFieldValue(packetListener, "Connection");
        if (connection == null) return null;

        return findAssignableFieldValue(connection, Channel.class);
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

    private static <T> T findAssignableFieldValue(Object instance, Class<T> wanted) {
        Class<?> type = instance.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (!wanted.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(instance);
                    if (wanted.isInstance(value)) return wanted.cast(value);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }
}
