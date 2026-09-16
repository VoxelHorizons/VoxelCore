package org.voxelhorizons.content.block;

/** Stable carrier-family slot assigned to one content block. */
public final class BlockAllocation {
    private final BlockMethod method;
    private final int slot;
    private final boolean active;

    public BlockAllocation(BlockMethod method, int slot, boolean active) {
        if (method == null || method == BlockMethod.AUTO) throw new IllegalArgumentException("Allocation method must be concrete");
        if (slot < 0) throw new IllegalArgumentException("Block allocation slot cannot be negative");
        this.method = method;
        this.slot = slot;
        this.active = active;
    }

    public BlockMethod method() { return method; }
    public int slot() { return slot; }
    public boolean active() { return active; }
    public BlockAllocation withActive(boolean value) { return new BlockAllocation(method, slot, value); }
}
