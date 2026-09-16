package org.voxelhorizons.content.runtime;

/** Result returned to commands/callers after a content reload attempt. */
public final class ContentReloadResult {
    private final boolean success;
    private final long activeRevision;
    private final int itemCount;
    private final int blockCount;
    private final String message;

    private ContentReloadResult(boolean success, long activeRevision, int itemCount, int blockCount, String message) {
        this.success = success;
        this.activeRevision = activeRevision;
        this.itemCount = itemCount;
        this.blockCount = blockCount;
        this.message = message;
    }

    public static ContentReloadResult success(ContentSnapshot snapshot) {
        return new ContentReloadResult(true, snapshot.revision(), snapshot.items().size(), snapshot.blocks().size(), null);
    }

    public static ContentReloadResult failure(ContentSnapshot active, String message) {
        return new ContentReloadResult(false, active.revision(), active.items().size(), active.blocks().size(), message);
    }

    public boolean success() { return success; }
    public long activeRevision() { return activeRevision; }
    public int itemCount() { return itemCount; }
    public int blockCount() { return blockCount; }
    public String message() { return message; }
}
