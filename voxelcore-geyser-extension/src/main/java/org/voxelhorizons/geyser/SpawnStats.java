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
        return out.toString();
    }

    private static String limit(Map<String, Long> counts) {
        StringBuilder text = new StringBuilder("[");
        int shown = 0;
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            if (shown++ == 6) { text.append(", ..."); break; }
            if (shown > 1) text.append(", ");
            text.append(entry.getKey()).append(" x").append(entry.getValue());
        }
        return text.append(']').toString();
    }
}
