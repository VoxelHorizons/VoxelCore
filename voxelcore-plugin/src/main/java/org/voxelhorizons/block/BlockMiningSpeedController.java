package org.voxelhorizons.block;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.block.BlockDefinition;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/** Applies block-specific hardness through the native 1.20.5+ player mining-speed attribute. */
public final class BlockMiningSpeedController implements Listener {
    private static final UUID MODIFIER_ID = UUID.fromString("e299f781-1276-43c8-929d-b84e45af7e7d");
    private static final double MINIMUM_MULTIPLIER = 0.0001D;
    private static final double MAXIMUM_MULTIPLIER = 1024.0D;

    private final Plugin plugin;
    private final BlockManager blocks;
    private final AttributeBridge bridge;
    private final Map<UUID, Object> activeModifiers = new HashMap<UUID, Object>();
    private boolean warned;

    public BlockMiningSpeedController(Plugin plugin, BlockManager blocks, boolean enabled) {
        this.plugin = plugin;
        this.blocks = blocks;
        this.bridge = enabled ? AttributeBridge.create() : null;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (bridge == null) {
            plugin.getLogger().info("Custom block hardness uses carrier-native mining speed on this Minecraft version.");
            return;
        }
        registerAbortEvent();
        plugin.getLogger().info("Enabled native custom block hardness through the player block-break-speed attribute.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        clear(player);
        if (bridge == null || player.getGameMode() == GameMode.CREATIVE) return;

        Optional<ContentID> id = blocks.identify(event.getBlock());
        if (!id.isPresent()) return;
        BlockDefinition definition = blocks.getDefinition(id.get()).orElse(null);
        if (definition == null) return;

        if (definition.hardness() == 0.0D) {
            event.setInstaBreak(true);
            return;
        }

        double multiplier = hardnessMultiplier(
                bridge.hardness(event.getBlock().getType()), definition.hardness());
        if (Math.abs(multiplier - 1.0D) < 0.000001D) return;
        try {
            Object modifier = bridge.add(player, multiplier);
            if (modifier != null) activeModifiers.put(player.getUniqueId(), modifier);
        } catch (ReflectiveOperationException exception) {
            warnOnce("Unable to apply custom block mining speed", exception);
        } catch (RuntimeException exception) {
            warnOnce("Unable to apply custom block mining speed", exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) { clear(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) { clear(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { clear(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) { clear(event.getPlayer()); }

    public void clear(Player player) {
        if (bridge == null || player == null) return;
        Object modifier = activeModifiers.remove(player.getUniqueId());
        if (modifier == null) return;
        try {
            bridge.remove(player, modifier);
        } catch (ReflectiveOperationException exception) {
            warnOnce("Unable to clear custom block mining speed", exception);
        } catch (RuntimeException exception) {
            warnOnce("Unable to clear custom block mining speed", exception);
        }
    }

    public void clearAll() {
        if (bridge == null) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) clear(player);
        activeModifiers.clear();
    }

    static double hardnessMultiplier(double carrierHardness, double configuredHardness) {
        if (configuredHardness <= 0.0D || carrierHardness <= 0.0D) return 1.0D;
        return Math.max(MINIMUM_MULTIPLIER,
                Math.min(MAXIMUM_MULTIPLIER, carrierHardness / configuredHardness));
    }

    @SuppressWarnings("unchecked")
    private void registerAbortEvent() {
        try {
            Class<?> raw = Class.forName("org.bukkit.event.block.BlockDamageAbortEvent");
            final Method getPlayer = raw.getMethod("getPlayer");
            plugin.getServer().getPluginManager().registerEvent((Class<? extends Event>) raw, this,
                    EventPriority.MONITOR, new EventExecutor() {
                        @Override public void execute(Listener listener, Event event) throws EventException {
                            try {
                                clear((Player) getPlayer.invoke(event));
                            } catch (IllegalAccessException exception) {
                                throw new EventException(exception);
                            } catch (InvocationTargetException exception) {
                                throw new EventException(exception.getCause());
                            }
                        }
                    }, plugin, false);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Block-damage abort cleanup is unavailable: " + exception.getMessage());
        }
    }

    private void warnOnce(String message, Exception exception) {
        if (warned) return;
        warned = true;
        plugin.getLogger().log(Level.WARNING, message + "; falling back to carrier-native speed", exception);
    }

    private static final class AttributeBridge {
        private final Object attribute;
        private final Method getAttribute;
        private final Method addModifier;
        private final Method removeModifier;
        private final Method materialHardness;
        private final Constructor<?> modifierConstructor;
        private final Object multiplyOperation;

        private AttributeBridge(Object attribute, Method getAttribute, Method addModifier,
                                Method removeModifier, Method materialHardness,
                                Constructor<?> modifierConstructor, Object multiplyOperation) {
            this.attribute = attribute;
            this.getAttribute = getAttribute;
            this.addModifier = addModifier;
            this.removeModifier = removeModifier;
            this.materialHardness = materialHardness;
            this.modifierConstructor = modifierConstructor;
            this.multiplyOperation = multiplyOperation;
        }

        static AttributeBridge create() {
            try {
                Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
                Object attribute = field(attributeClass,
                        "PLAYER_BLOCK_BREAK_SPEED", "BLOCK_BREAK_SPEED").get(null);
                Class<?> instanceClass = Class.forName("org.bukkit.attribute.AttributeInstance");
                Class<?> modifierClass = Class.forName("org.bukkit.attribute.AttributeModifier");
                Class<?> operationClass = Class.forName("org.bukkit.attribute.AttributeModifier$Operation");
                @SuppressWarnings({"rawtypes", "unchecked"})
                Object operation = Enum.valueOf((Class<? extends Enum>) operationClass, "MULTIPLY_SCALAR_1");
                return new AttributeBridge(attribute,
                        Player.class.getMethod("getAttribute", attributeClass),
                        instanceClass.getMethod("addModifier", modifierClass),
                        instanceClass.getMethod("removeModifier", modifierClass),
                        Material.class.getMethod("getHardness"),
                        modifierClass.getConstructor(UUID.class, String.class, double.class, operationClass),
                        operation);
            } catch (ReflectiveOperationException unavailable) {
                return null;
            } catch (LinkageError unavailable) {
                return null;
            }
        }

        double hardness(Material material) {
            try {
                return ((Number) materialHardness.invoke(material)).doubleValue();
            } catch (ReflectiveOperationException exception) {
                return 1.0D;
            }
        }

        Object add(Player player, double multiplier) throws ReflectiveOperationException {
            Object instance = getAttribute.invoke(player, attribute);
            if (instance == null) return null;
            Object modifier = modifierConstructor.newInstance(MODIFIER_ID, "voxelcore_custom_hardness",
                    multiplier - 1.0D, multiplyOperation);
            addModifier.invoke(instance, modifier);
            return modifier;
        }

        void remove(Player player, Object modifier) throws ReflectiveOperationException {
            Object instance = getAttribute.invoke(player, attribute);
            if (instance != null) removeModifier.invoke(instance, modifier);
        }

        private static Field field(Class<?> type, String... names) throws NoSuchFieldException {
            for (String name : names) {
                try { return type.getField(name); }
                catch (NoSuchFieldException ignored) { }
            }
            throw new NoSuchFieldException(java.util.Arrays.toString(names));
        }
    }
}
