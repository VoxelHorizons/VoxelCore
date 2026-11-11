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

        assertEquals(ContentID.of("voxel", "ruby_ore"), resolver.renderCreated);
        assertNull("Item provider should use the rendering factory", resolver.created);
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

        assertEquals(ContentID.of("voxel", "ui-blank"), resolver.renderCreated);
        assertNull(resolver.created);
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
    public void amountSelectionButtonsSupportAbstractRenderModelsWithoutCreatingShopStock() {
        FakeResolver resolver = new FakeResolver();
        resolver.abstractItem = ContentID.of("voxel", "ui-blank");
        VoxelCoreItemProvider provider = new VoxelCoreItemProvider(resolver, Logger.getAnonymousLogger());
        YamlConfiguration config = new YamlConfiguration();

        // Exact startup exception: amountSelectionGUI.buttons.set1.item
        ConfigurationSection setOne = config.createSection("amountSelectionGUI.buttons.set1.item");
        setOne.set("voxelcore", "voxel:ui-blank");
        ItemStack icon = provider.loadItem(setOne);

        assertTrue(provider.isValidItem(icon));
        assertEquals(ContentID.of("voxel", "ui-blank"), resolver.renderCreated);
        assertNull("Quantity buttons must not use the tradeable item factory", resolver.created);
    }

    @Test
    public void bulkAmountGuiAndShopSelectorIconsAlsoUseRenderFactory() {
        FakeResolver resolver = new FakeResolver();
        resolver.abstractItem = ContentID.of("voxel", "ui-blank");
        VoxelCoreItemProvider provider = new VoxelCoreItemProvider(resolver, Logger.getAnonymousLogger());
        YamlConfiguration config = new YamlConfiguration();

        ConfigurationSection bulk = config.createSection("amountSelectionGUIBulkBuy.buttons.buy1.item");
        bulk.set("voxelcore", "voxel:ui-blank");
        assertTrue(provider.isValidItem(provider.loadItem(bulk)));
        assertNull(resolver.created);

        resolver.renderCreated = null;
        ConfigurationSection category = config.createSection("shopMenuItems.1.item");
        category.set("voxelcore", "voxel:ui-blank");
        assertTrue(provider.isValidItem(provider.loadItem(category)));
        assertEquals(ContentID.of("voxel", "ui-blank"), resolver.renderCreated);
        assertNull(resolver.created);
    }

    @Test
    public void specialElementsAllowAbstractIconsUsingTheRenderOnlyFactory() {
        FakeResolver resolver = new FakeResolver();
        resolver.abstractItem = ContentID.of("voxel", "ui-blank");
        VoxelCoreItemProvider provider = new VoxelCoreItemProvider(resolver, Logger.getAnonymousLogger());
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection balance = config.createSection("specialElements.balance.item");
        balance.set("voxelcore", "voxel:ui/blank");

        ItemStack icon = provider.loadItem(balance);

        assertTrue(provider.isValidItem(icon));
        assertEquals(ContentID.of("voxel", "ui-blank"), resolver.renderCreated);
        assertNull("Balance icon must not invoke tradeable factory", resolver.created);
    }

    @Test
    public void rendersItemsWithoutHardcodedShopGuiConfigurationPaths() {
        FakeResolver resolver = new FakeResolver();
        resolver.abstractItem = ContentID.of("voxel", "ui-blank");
        VoxelCoreItemProvider provider = new VoxelCoreItemProvider(resolver, Logger.getAnonymousLogger());
        YamlConfiguration config = new YamlConfiguration();

        for (String path : new String[] {
                "specialElements.balance.item",
                "buttons.goBack.item",
                "shops.farming.items.1",
                "arbitrary.future.section"
        }) {
            ConfigurationSection section = config.createSection(path);
            section.set("voxelcore", "voxel:ui/blank");
            resolver.renderCreated = null;
            ItemStack rendered = provider.loadItem(section);

            assertTrue(provider.isValidItem(rendered));
            assertEquals("Renderer must not depend on config path " + path,
                    ContentID.of("voxel", "ui-blank"), resolver.renderCreated);
            assertNull("Provider must not use the grant-only factory", resolver.created);
        }
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
