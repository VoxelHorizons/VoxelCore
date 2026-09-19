package org.voxelhorizons.block;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.item.ItemManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

/** Paper's optional pick-block event, loaded reflectively to keep legacy distributions compatible. */
public final class BlockPickListener implements Listener {
    private static final String EVENT = "io.papermc.paper.event.player.PlayerPickBlockEvent";

    private final Plugin plugin;
    private final BlockManager blocks;
    private final ItemManager items;

    public BlockPickListener(Plugin plugin, BlockManager blocks, ItemManager items) {
        this.plugin = plugin;
        this.blocks = blocks;
        this.items = items;
    }

    @SuppressWarnings("unchecked")
    public void registerIfAvailable() {
        try {
            Class<?> eventType = Class.forName(EVENT);
            final Method getPlayer = eventType.getMethod("getPlayer");
            final Method getBlock = eventType.getMethod("getBlock");
            final Method getTargetSlot = eventType.getMethod("getTargetSlot");
            final Method setCancelled = eventType.getMethod("setCancelled", Boolean.TYPE);
            plugin.getServer().getPluginManager().registerEvent((Class<? extends Event>) eventType, this,
                    EventPriority.HIGHEST, new EventExecutor() {
                        @Override public void execute(Listener listener, Event event) throws EventException {
                            try {
                                Player player = (Player) getPlayer.invoke(event);
                                if (player.getGameMode() != GameMode.CREATIVE) return;
                                Optional<ContentID> id = blocks.identify((Block) getBlock.invoke(event));
                                if (!id.isPresent() || !items.hasItem(id.get())) return;
                                int slot = ((Number) getTargetSlot.invoke(event)).intValue();
                                if (slot < 0 || slot > 8) return;
                                setCancelled.invoke(event, true);
                                selectItem(player, id.get(), slot);
                            } catch (IllegalAccessException exception) {
                                throw new EventException(exception);
                            } catch (InvocationTargetException exception) {
                                throw new EventException(exception.getCause());
                            }
                        }
                    }, plugin, true);
            plugin.getLogger().info("Enabled Creative pick-block support for VoxelCore blocks.");
        } catch (ClassNotFoundException unavailable) {
            plugin.getLogger().info("Creative pick-block event unavailable on this server version.");
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Unable to register Creative pick-block support: " + exception.getMessage());
        }
    }

    private void selectItem(final Player player, final ContentID id, final int targetSlot) {
        // Apply after the packet handler so a vanilla carrier pick cannot overwrite the custom item.
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                if (!player.isOnline() || player.getGameMode() != GameMode.CREATIVE || !items.hasItem(id)) return;
                int source = findItem(player, id);
                if (source >= 0 && source != targetSlot) {
                    ItemStack previous = player.getInventory().getItem(targetSlot);
                    player.getInventory().setItem(targetSlot, player.getInventory().getItem(source));
                    player.getInventory().setItem(source, previous);
                } else if (source < 0) {
                    player.getInventory().setItem(targetSlot, items.createItem(id));
                }
                player.getInventory().setHeldItemSlot(targetSlot);
                player.updateInventory();
            }
        });
    }

    private int findItem(Player player, ContentID id) {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack != null && id.equals(items.identify(stack).orElse(null))) return slot;
        }
        return -1;
    }
}
