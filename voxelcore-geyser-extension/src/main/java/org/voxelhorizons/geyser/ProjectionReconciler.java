package org.voxelhorizons.geyser;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Pushes Paper projection changes to Bedrock entities that are already spawned.
 *
 * Spawn-time substitution covers chunk reloads and reconnects, because Geyser
 * raises a new spawn event per session whenever Paper re-sends the entity, and
 * Geyser itself already translates Java position/rotation packets and entity
 * removal. What a spawn event cannot see is a projection that changes while the
 * entity stays loaded; this class handles that delta without Geyser types so it
 * can be tested. Geyser cannot redefine a spawned entity, so a model change is
 * only reported: Paper must re-send the entity to apply the new definition.
 */
final class ProjectionReconciler {
    /** A live Bedrock-side entity in one session for a Paper UUID. */
    interface Target {
        /** Identifier of the definition this entity was spawned with. */
        String definitionId();
        /** Override Geyser SCALE; null restores Geyser's own value. */
        void scale(Float scale);
        /** Set the voxelcore:dye entity property (ignored for entities without it). */
        default void dye(int dye) { }
    }

    static final class Result {
        final int changed, scaled, needsRespawn;
        Result(int changed,int scaled,int needsRespawn) {
            this.changed=changed;this.scaled=scaled;this.needsRespawn=needsRespawn;
        }
    }

    private Map<UUID,EntityProjectionIndex.Projection> applied=Collections.emptyMap();

    /**
     * @param next current authoritative snapshot (from {@link EntityProjectionIndex#snapshot()})
     * @param lookup live targets for a Paper UUID across all Bedrock sessions
     */
    synchronized Result reconcile(Map<UUID,EntityProjectionIndex.Projection> next,
                                  Function<UUID,Collection<? extends Target>> lookup) {
        int changed=0,scaled=0,needsRespawn=0;
        for(Map.Entry<UUID,EntityProjectionIndex.Projection> entry:next.entrySet()) {
            EntityProjectionIndex.Projection now=entry.getValue();
            if(now.equals(applied.get(entry.getKey())))continue;
            changed++;
            Collection<? extends Target> targets=lookup.apply(entry.getKey());
            if(targets==null)continue;
            for(Target target:targets) {
                if(now.entityType().equals(target.definitionId())) {
                    target.scale(now.scale());
                    if(now.dye()!=null)target.dye(now.dye().intValue());
                    scaled++;
                } else {
                    needsRespawn++;
                }
            }
        }
        // Entries that left the feed are not touched: either Paper removed the
        // entity (Geyser despawns it) or the feed went stale (fail closed: keep
        // the last visual rather than guessing a new one).
        applied=Collections.unmodifiableMap(new HashMap<UUID,EntityProjectionIndex.Projection>(next));
        return new Result(changed,scaled,needsRespawn);
    }
}
