package org.voxelhorizons.integration.shopgui;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;
import org.voxelhorizons.content.ContentID;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class VoxelCoreItemProviderTest {

    @Test
    public void loadsConfiguredContentIdThroughVoxelCoreResolver() {
        FakeResolver resolver = new FakeResolver();
        VoxelCoreItemProvider provider = new VoxelCoreItemProvider(resolver, Logger.getAnonymousLogger());
        YamlConfiguration config = new YamlConfiguration();
        config.set("voxelcore", "voxel:ruby_ore");

        ItemStack loaded = provider.loadItem(config);

        assertEquals(ContentID.of("voxel", "ruby_ore"), resolver.created);
        assertTrue(provider.isValidItem(loaded));
    }

    @Test
    public void comparesPersistentContentIdentityInsteadOfStackAppearance() {
        FakeResolver resolver = new FakeResolver();
        VoxelCoreItemProvider provider = new VoxelCoreItemProvider(resolver, Logger.getAnonymousLogger());
        ItemStack first = resolver.identified(ContentID.of("voxel", "ruby"));
        ItemStack same = resolver.identified(ContentID.of("voxel", "ruby"));
        ItemStack different = resolver.identified(ContentID.of("voxel", "sapphire"));

        assertTrue(provider.compare(first, same));
        assertFalse(provider.compare(first, different));
        assertFalse(provider.compare(new ItemStack(Material.STONE), new ItemStack(Material.STONE)));
    }

    @Test
    public void rejectsMissingAndInvalidReferencesWithoutCrashingShopLoad() {
        FakeResolver resolver = new FakeResolver();
        resolver.rejectCreates = true;
        VoxelCoreItemProvider provider = new VoxelCoreItemProvider(resolver, Logger.getAnonymousLogger());
        YamlConfiguration missing = new YamlConfiguration();
        YamlConfiguration unknown = new YamlConfiguration();
        unknown.set("voxelcore", "voxel:missing");

        assertNull(provider.loadItem(missing));
        assertNull(provider.loadItem(unknown));
    }

    private static final class FakeResolver implements VoxelCoreItemResolver {
        private final Map<ItemStack, ContentID> identities = new IdentityHashMap<ItemStack, ContentID>();
        private ContentID created;
        private boolean rejectCreates;

        @Override
        public ItemStack create(ContentID id) {
            created = id;
            if (rejectCreates) throw new IllegalArgumentException("Unknown item: " + id);
            return identified(id);
        }

        @Override
        public Optional<ContentID> identify(ItemStack stack) {
            return Optional.ofNullable(identities.get(stack));
        }

        private ItemStack identified(ContentID id) {
            ItemStack stack = new ItemStack(Material.PAPER);
            identities.put(stack, id);
            return stack;
        }
    }
}
