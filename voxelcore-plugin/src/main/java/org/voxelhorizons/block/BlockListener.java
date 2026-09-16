package org.voxelhorizons.block;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.action.ActionContext;
import org.voxelhorizons.action.ActionExecutor;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.block.BlockDefinition;
import org.voxelhorizons.item.ItemManager;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Optional;

/** Protects carrier states and applies custom-block interaction and drop behavior. */
public final class BlockListener implements Listener {
    private final BlockManager blocks;
    private final ItemManager items;
    private final ActionExecutor actions;

    public BlockListener(BlockManager blocks, ItemManager items, ActionExecutor actions) {
        this.blocks = blocks;
        this.items = items;
        this.actions = actions;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Optional<ContentID> id = blocks.identify(event.getBlock());
        if (!id.isPresent()) return;
        BlockDefinition definition = blocks.getDefinition(id.get()).orElse(null);
        if (definition == null) return;
        suppressVanillaDrops(event);
        ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
        if (definition.dropWhenMined()) {
            ContentID drop = held != null && held.containsEnchantment(Enchantment.SILK_TOUCH)
                    ? definition.silkTouchItem() : definition.dropItem();
            if (drop != null && items.hasItem(drop)) {
                event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), items.createItem(drop));
            }
        }
        actions.execute(definition.events().forEvent("placed_block.break"),
                new ActionContext(event.getPlayer(), held, items.identify(held).orElse(null), event.getBlock(),
                        id.get(), null, event));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        Optional<ContentID> id = blocks.identify(clicked);
        if (!id.isPresent()) return;
        BlockDefinition definition = blocks.getDefinition(id.get()).orElse(null);
        if (definition == null) return;
        ItemStack held = event.getItem();
        actions.execute(definition.events().forEvent("placed_block.interact"),
                new ActionContext(event.getPlayer(), held, items.identify(held).orElse(null), clicked,
                        id.get(), event.getBlockFace(), event));
        // Prevent note-block tuning and other vanilla carrier interactions from changing the allocated state.
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (blocks.identify(event.getBlock()).isPresent()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNote(NotePlayEvent event) {
        if (blocks.identify(event.getBlock()).isPresent()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        protectExplosionImmune(event.blockList().iterator());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        protectExplosionImmune(event.blockList().iterator());
    }

    private void protectExplosionImmune(Iterator<Block> affected) {
        while (affected.hasNext()) {
            Block block = affected.next();
            Optional<ContentID> id = blocks.identify(block);
            if (id.isPresent() && blocks.getDefinition(id.get()).map(BlockDefinition::explosionImmune).orElse(false)) {
                affected.remove();
            }
        }
    }

    private static void suppressVanillaDrops(BlockBreakEvent event) {
        try {
            Method method = BlockBreakEvent.class.getMethod("setDropItems", boolean.class);
            method.invoke(event, Boolean.FALSE);
        } catch (ReflectiveOperationException legacy) {
            event.setCancelled(true);
            event.getBlock().setType(Material.AIR);
        }
    }
}
