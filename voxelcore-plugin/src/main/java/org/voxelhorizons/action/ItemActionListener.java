package org.voxelhorizons.action;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.action.ActionDefinition;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.item.ItemManager;

import java.util.List;
import java.util.Optional;

/** Dispatches authored item interaction events into the shared action executor. */
public final class ItemActionListener implements Listener {
    private final ItemManager items;
    private final ActionExecutor actions;

    public ItemActionListener(ItemManager items, ActionExecutor actions) {
        this.items = items;
        this.actions = actions;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack stack = event.getItem();
        Optional<ContentID> id = items.identify(stack);
        if (!id.isPresent()) return;
        Optional<ItemDefinition> definition = items.getDefinition(id.get());
        if (!definition.isPresent()) return;
        String trigger = trigger(event);
        if (trigger == null) return;
        List<ActionDefinition> configured = definition.get().events().forEvent(trigger);
        if (configured.isEmpty()) return;
        actions.execute(configured, new ActionContext(event.getPlayer(), stack, id.get(), event.getClickedBlock(),
                null, event.getBlockFace(), event));
    }

    private static String trigger(PlayerInteractEvent event) {
        boolean shift = event.getPlayer().isSneaking();
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            return shift ? "interact.right_shift" : "interact.right";
        }
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            return shift ? "interact.left_shift" : "interact.left";
        }
        return null;
    }
}
