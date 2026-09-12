package org.voxelhorizons.item;

import org.bukkit.inventory.ItemStack;
import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.item.ItemRenderDefinition;
import org.voxelhorizons.content.item.ItemType;
import org.voxelhorizons.content.render.RenderAllocation;
import org.voxelhorizons.content.render.RenderAllocationRegistry;
import org.voxelhorizons.content.runtime.ContentRuntime;
import org.voxelhorizons.content.runtime.ContentSnapshot;
import org.voxelhorizons.platform.Capabilities;
import org.voxelhorizons.platform.Version;
import org.voxelhorizons.platform.VersionAdapter;
import org.voxelhorizons.platform.item.ItemPlatformAdapter;

import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class ItemManagerRenderAllocationTest {
    @Test
    public void passesSnapshotAllocationToPlatformAdapter() {
        ContentID id = ContentID.of("test", "ruby");
        ItemDefinition definition = new ItemDefinition(id, ItemType.ITEM, "minecraft:paper", "Ruby",
                Collections.<String>emptyList(), false, Optional.<ContentID>empty(),
                new ItemRenderDefinition("test:item/ruby", null), Collections.<String, Object>emptyMap());
        ItemDefinitionRegistry items = new ItemDefinitionRegistry(Collections.singletonMap(id, definition));
        RenderAllocation allocation = new RenderAllocation(1000, "test:item/ruby", true);
        RenderAllocationRegistry allocations = new RenderAllocationRegistry(1001, Collections.singletonMap(id, allocation));
        ContentRuntime runtime = new ContentRuntime(new ContentSnapshot(1L, items, allocations));
        CapturingItems adapter = new CapturingItems();
        ItemManager manager = new ItemManager(runtime, new StubVersionAdapter(adapter));

        manager.createItem(id, 1);
        assertEquals(1000, adapter.allocation.customModelData());
    }

    private static final class StubVersionAdapter implements VersionAdapter {
        private final ItemPlatformAdapter items;
        private StubVersionAdapter(ItemPlatformAdapter items) { this.items = items; }
        @Override public Version version() { return Version.parse("1.14.4"); }
        @Override public Capabilities capabilities() { return new Capabilities(true, true, false, false, false, false); }
        @Override public ItemPlatformAdapter items() { return items; }
    }

    private static final class CapturingItems implements ItemPlatformAdapter {
        private RenderAllocation allocation;
        @Override public ItemStack createItem(ItemDefinition definition, int quantity) { return null; }
        @Override public ItemStack createItem(ItemDefinition definition, int quantity, RenderAllocation allocation) {
            this.allocation = allocation;
            return null;
        }
        @Override public Optional<ContentID> getContentId(ItemStack stack) { return Optional.empty(); }
        @Override public ItemStack setContentId(ItemStack stack, ContentID id) { return stack; }
    }
}
