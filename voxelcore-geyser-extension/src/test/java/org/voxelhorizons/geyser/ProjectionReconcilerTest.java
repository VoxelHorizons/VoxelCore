package org.voxelhorizons.geyser;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public final class ProjectionReconcilerTest {
    private static final String BENCH="voxelcore:entity_i_bench", CHAIR="voxelcore:entity_i_chair";

    private static final class FakeEntity implements ProjectionReconciler.Target {
        final String definition;
        final List<Float> scales=new ArrayList<Float>();
        FakeEntity(String definition) { this.definition=definition; }
        @Override public String definitionId() { return definition; }
        @Override public void scale(Float scale) { scales.add(scale); }
    }

    private static Map<UUID,EntityProjectionIndex.Projection> state(UUID id,String type,Float scale) {
        Map<UUID,EntityProjectionIndex.Projection> map=new HashMap<UUID,EntityProjectionIndex.Projection>();
        map.put(id,EntityProjectionIndex.Projection.of(type,scale));
        return map;
    }

    @Test public void pushesOnlyChangedScaleToEverySessionAndClearsRemovedScale() {
        UUID id=UUID.randomUUID();
        FakeEntity first=new FakeEntity(BENCH), second=new FakeEntity(BENCH);
        List<FakeEntity> live=List.of(first,second);
        ProjectionReconciler reconciler=new ProjectionReconciler();

        ProjectionReconciler.Result initial=reconciler.reconcile(state(id,BENCH,2f),u->live);
        assertEquals(1,initial.changed);
        assertEquals(2,initial.scaled);
        assertEquals(List.of(2f),first.scales);

        ProjectionReconciler.Result unchanged=reconciler.reconcile(state(id,BENCH,2f),u->live);
        assertEquals("Unchanged heartbeat rewrites must not resend data",0,unchanged.changed);
        assertEquals(1,first.scales.size());

        reconciler.reconcile(state(id,BENCH,0.5f),u->live);
        assertEquals(List.of(2f,0.5f),second.scales);

        reconciler.reconcile(state(id,BENCH,null),u->live);
        assertEquals("Dropping a scale restores Geyser's own value",
                java.util.Arrays.asList(2f,0.5f,null),first.scales);
    }

    @Test public void modelChangeOnSpawnedEntityIsReportedNotForced() {
        UUID id=UUID.randomUUID();
        FakeEntity spawned=new FakeEntity(BENCH), vanilla=new FakeEntity("minecraft:item_display");
        ProjectionReconciler reconciler=new ProjectionReconciler();
        reconciler.reconcile(state(id,BENCH,null),u->List.of(spawned));
        ProjectionReconciler.Result result=reconciler.reconcile(state(id,CHAIR,3f),u->List.of(spawned,vanilla));
        assertEquals(2,result.needsRespawn);
        assertEquals(0,result.scaled);
        assertEquals("Wrong-geometry entities never receive the new model's scale",
                Collections.singletonList((Float)null),spawned.scales);
        assertTrue(vanilla.scales.isEmpty());
    }

    @Test public void unloadedEntitiesAndExpiredFeedsAreNotTouched() {
        UUID id=UUID.randomUUID();
        FakeEntity entity=new FakeEntity(BENCH);
        ProjectionReconciler reconciler=new ProjectionReconciler();
        ProjectionReconciler.Result notLoaded=reconciler.reconcile(state(id,BENCH,2f),u->null);
        assertEquals(1,notLoaded.changed);
        assertEquals(0,notLoaded.scaled);
        // Expired/removed feed: the last visual stays, no clear is pushed.
        Collection<FakeEntity> live=List.of(entity);
        assertEquals(0,reconciler.reconcile(Collections.emptyMap(),u->live).scaled);
        assertTrue(entity.scales.isEmpty());
        // A renewed feed re-applies its state to entities spawned meanwhile.
        assertEquals(1,reconciler.reconcile(state(id,BENCH,2f),u->live).scaled);
        assertEquals(List.of(2f),entity.scales);
    }
}
