package org.voxelhorizons.integration.shopgui;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
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
    public void acceptsResourceStylePathAsHyphenatedContentId() {
        FakeResolver resolver = new FakeResolver();
        VoxelCoreItemProvider provider = new VoxelCoreItemProvider(resolver, Logger.getAnonymousLogger());
        YamlConfiguration config = new YamlConfiguration();
        config.set("voxelcore", "voxel:ui/blank");

        provider.loadItem(config);

        assertEquals(ContentID.of("voxel", "ui-blank"), resolver.created);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownReferenceDuringShopLoad() {
        FakeResolver resolver = new FakeResolver();
        resolver.rejectCreates = true;
        VoxelCoreItemProvider provider = new VoxelCoreItemProvider(resolver, Logger.getAnonymousLogger());
        YamlConfiguration unknown = new YamlConfiguration();
        unknown.set("voxelcore", "voxel:missing");

        provider.loadItem(unknown);
    }


    @Test
    public void decorativeShopGuiButtonsAllowRenderableAbstractItems() {
        FakeResolver resolver = new FakeResolver();
        resolver.abstractItem = ContentID.of("voxel", "ui-blank");
        VoxelCoreItemProvider provider = new VoxelCoreItemProvider(resolver, Logger.getAnonymousLogger());
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection button = config.createSection("buttons.goBack.item");
        button.set("voxelcore", "voxel:ui-blank");

        ItemStack icon = provider.loadItem(button);

        assertEquals(ContentID.of("voxel", "ui-blank"), resolver.renderCreated);
        assertNull("decorative button should not invoke tradeable factory", resolver.created);
        assertTrue(provider.isValidItem(icon));
    }

    @Test
    public void abstractItemsRemainUntradeableEvenWhenUsedAsShopStock() {
        FakeResolver resolver = new FakeResolver();
        resolver.abstractItem = ContentID.of("voxel", "ui-blank");
        VoxelCoreItemProvider provider = new VoxelCoreItemProvider(resolver, Logger.getAnonymousLogger());
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection product = config.createSection("shops.farming.items.1");
        product.set("voxelcore", "voxel:ui-blank");

        try {
            provider.loadItem(product);
            org.junit.Assert.fail("Abstract items must never be accepted as shop stock");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Cannot create abstract item"));
            assertTrue(expected.getMessage().contains("shops.farming.items.1"));
        }
        assertEquals(ContentID.of("voxel", "ui-blank"), resolver.created);
        assertNull(resolver.renderCreated);
    }

    @Test
    public void unrenderableAbstractButtonStillFailsAtAuthoredConfiguration() {
        FakeResolver resolver = new FakeResolver();
        resolver.rejectRender = true;
        VoxelCoreItemProvider provider = new VoxelCoreItemProvider(resolver, Logger.getAnonymousLogger());
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection button = config.createSection("buttons.goBack.item");
        button.set("voxelcore", "voxel:ui-blank");

        try {
            provider.loadItem(button);
            org.junit.Assert.fail("Unrenderable abstract icon must not result in a null GuiButton");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("buttons.goBack.item"));
            assertTrue(expected.getMessage().contains("renderable model and material"));
        }
    }

    @Test
    public void onlyTopLevelGlobalButtonPathsHaveDisplayPrivileges() {
        assertTrue(VoxelCoreItemProvider.isGuiButtonItem("buttons.goBack.item"));
        assertTrue(VoxelCoreItemProvider.isGuiButtonItem("buttons.back.item"));
        assertFalse(VoxelCoreItemProvider.isGuiButtonItem(null));
        assertFalse(VoxelCoreItemProvider.isGuiButtonItem("buttons.goBack"));
        assertFalse(VoxelCoreItemProvider.isGuiButtonItem("buttons.goBack.item.other"));
        assertFalse(VoxelCoreItemProvider.isGuiButtonItem("shops.test.buttons.goBack.item"));
        assertFalse(VoxelCoreItemProvider.isGuiButtonItem("shops.test.items.1"));
        assertFalse(VoxelCoreItemProvider.isGuiButtonItem("buttons.goBack.price"));
        assertFalse(VoxelCoreItemProvider.isGuiButtonItem("buttons..item"));
    }

    @Test
    public void ignoresSectionsOwnedByAnotherItemProvider() {
        VoxelCoreItemProvider provider = new VoxelCoreItemProvider(
                new FakeResolver(), Logger.getAnonymousLogger());

        assertNull(provider.loadItem(new YamlConfiguration()));
    }

    private static final class FakeResolver implements VoxelCoreItemResolver {
        private final Map<ItemStack, ContentID> identities = new IdentityHashMap<ItemStack, ContentID>();
        private ContentID created;
        private boolean rejectCreates;
        private boolean rejectRender;
        private ContentID abstractItem;
        private ContentID renderCreated;

        @Override
        public ItemStack create(ContentID id) {
            created = id;
            if (rejectCreates) throw new IllegalArgumentException("Unknown item: " + id);
            if (id.equals(abstractItem)) throw new IllegalArgumentException("Cannot create abstract item: " + id);
            return identified(id);
        }

        @Override
        public ItemStack createRender(ContentID id) {
            renderCreated = id;
            if (rejectRender) throw new IllegalArgumentException(
                    "Abstract item has no renderable model and material: " + id);
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
