package org.voxelhorizons.platform.v26_2;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import org.bukkit.entity.Player;
import org.voxelhorizons.platform.network.PacketChannelAdapter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    public boolean closeClientScreen(Player player) {
        if (player == null) return false;

        try {
            Channel channel = channels.get(player.getUniqueId());
            if (channel == null) channel = resolveChannel(player);
            if (channel == null) return false;

            ClassLoader loader = player.getClass().getClassLoader();
            Class<?> packetClass = Class.forName(
                    "net.minecraft.network.protocol.game.ClientboundContainerClosePacket",
                    true,
                    loader);
            Object packet = packetClass.getConstructor(int.class).newInstance(0);

            final Channel target = channel;
            target.eventLoop().execute(() -> target.writeAndFlush(packet));
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public boolean blankClientAdvancements(Player player) {
        if (player == null) return false;

        try {
            Channel channel = channels.get(player.getUniqueId());
            if (channel == null) channel = resolveChannel(player);
            if (channel == null) return false;

            ClassLoader loader = player.getClass().getClassLoader();
            Class<?> updateClass = Class.forName(
                    "net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket",
                    true,
                    loader);
            Object resetPacket = updateClass
                    .getConstructor(boolean.class, Collection.class, Set.class, Map.class, boolean.class)
                    .newInstance(
                            true,
                            Collections.emptyList(),
                            Collections.emptySet(),
                            Collections.emptyMap(),
                            false);

            Class<?> selectClass = Class.forName(
                    "net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket",
                    true,
                    loader);
            Class<?> identifierClass = Class.forName(
                    "net.minecraft.resources.Identifier",
                    true,
                    loader);
            Object clearSelection = selectClass.getConstructor(identifierClass).newInstance(new Object[]{null});

            final Channel target = channel;
            target.eventLoop().execute(() -> {
                target.write(resetPacket);
                target.writeAndFlush(clearSelection);
            });
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public boolean restoreClientAdvancements(Player player) {
        if (player == null) return false;

        try {
            Channel channel = channels.get(player.getUniqueId());
            if (channel == null) channel = resolveChannel(player);
            if (channel == null) return false;

            Object serverPlayer = player.getClass().getMethod("getHandle").invoke(player);
            if (serverPlayer == null) return false;

            Method getAdvancements = serverPlayer.getClass().getMethod("getAdvancements");
            Object advancements = getAdvancements.invoke(serverPlayer);
            if (advancements == null) return false;

            Field visibleField = findDeclaredField(advancements.getClass(), "visible");
            Field progressField = findDeclaredField(advancements.getClass(), "progress");
            if (visibleField == null || progressField == null) return false;

            visibleField.setAccessible(true);
            progressField.setAccessible(true);

            Object visibleRaw = visibleField.get(advancements);
            Object progressRaw = progressField.get(advancements);
            if (!(visibleRaw instanceof Set) || !(progressRaw instanceof Map)) return false;

            @SuppressWarnings("unchecked")
            Set<Object> visible = (Set<Object>) visibleRaw;
            @SuppressWarnings("unchecked")
            Map<Object, Object> serverProgress = (Map<Object, Object>) progressRaw;

            List<Object> added = new ArrayList<>(visible);
            Map<Object, Object> progress = new LinkedHashMap<>();

            for (Object holder : visible) {
                Object advancementProgress = serverProgress.get(holder);
                if (advancementProgress == null) continue;
                Method idMethod = holder.getClass().getMethod("id");
                Object identifier = idMethod.invoke(holder);
                if (identifier != null) progress.put(identifier, advancementProgress);
            }

            ClassLoader loader = player.getClass().getClassLoader();
            Class<?> updateClass = Class.forName(
                    "net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket",
                    true,
                    loader);
            Object restorePacket = updateClass
                    .getConstructor(boolean.class, Collection.class, Set.class, Map.class, boolean.class)
                    .newInstance(
                            true,
                            added,
                            Collections.emptySet(),
                            progress,
                            false);

            final Channel target = channel;
            target.eventLoop().execute(() -> target.writeAndFlush(restorePacket));
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
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

    private static Field findDeclaredField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
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
