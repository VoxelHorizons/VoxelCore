package org.voxelhorizons.action;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.content.ContentID;

/** Runtime values available while executing a compiled action list. */
public final class ActionContext {
    private final Player player;
    private final ItemStack item;
    private final ContentID itemId;
    private final Block block;
    private final ContentID blockId;
    private final BlockFace face;
    private final Cancellable event;

    public ActionContext(Player player, ItemStack item, ContentID itemId, Block block,
                         ContentID blockId, BlockFace face, Cancellable event) {
        this.player = player;
        this.item = item;
        this.itemId = itemId;
        this.block = block;
        this.blockId = blockId;
        this.face = face;
        this.event = event;
    }

    public Player player() { return player; }
    public ItemStack item() { return item; }
    public ContentID itemId() { return itemId; }
    public Block block() { return block; }
    public ContentID blockId() { return blockId; }
    public BlockFace face() { return face; }
    public Cancellable event() { return event; }
}
