package org.voxelhorizons.geyser;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spawn substitution counters for {@code voxelcore status}, so an invisible placed model
 * can be traced: substituted, item_display not in any feed, fed model without a registered
 * Bedrock entity (pack built after Geyser started), or a fed UUID on a non-item_display type.
 */
final class SpawnStats {
    final AtomicLong substituted = new AtomicLong(), notInFeed = new AtomicLong(), rejected = new AtomicLong();
    private final Map<String, Long> unregistered = new TreeMap<String, Long>(), otherTypes = new TreeMap<String, Long>();
    private final Map<String, Long> seen = new TreeMap<String, Long>();

    /** Every Java spawn Geyser reported to extensions, by type (proves whether item_display arrives at all). */
    synchronized void seen(String javaType) { seen.merge(javaType, 1L, Long::sum); }

    synchronized void unregistered(String entityType) { unregistered.merge(entityType, 1L, Long::sum); }

    synchronized void otherType(String javaType) { otherTypes.merge(javaType, 1L, Long::sum); }

    synchronized String describe() {
        StringBuilder out = new StringBuilder();
        out.append(substituted.get()).append(" substituted, ")
                .append(notInFeed.get()).append(" item_display not in a projection feed, ")
                .append(rejected.get()).append(" rejected by Geyser");
        if (!unregistered.isEmpty())
            out.append("; fed models whose Bedrock entity is not registered (restart Geyser after a pack build): ")
                    .append(unregistered.size()).append(' ').append(limit(unregistered));
        if (!otherTypes.isEmpty())
            out.append("; fed UUIDs spawned as other entity types (not substituted): ").append(limit(otherTypes));
        long total = 0;
        for (long count : seen.values()) total += count;
        out.append("; spawn events from Geyser: ").append(total);
        if (!seen.isEmpty()) out.append(' ').append(limit(seen, 12));
        return out.toString();
    }

    private static String limit(Map<String, Long> counts) { return limit(counts, 6); }

    private static String limit(Map<String, Long> counts, int max) {
        StringBuilder text = new StringBuilder("[");
        int shown = 0;
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            if (shown++ == max) { text.append(", ..."); break; }
            if (shown > 1) text.append(", ");
            text.append(entry.getKey()).append(" x").append(entry.getValue());
        }
        return text.append(']').toString();
    }
}
