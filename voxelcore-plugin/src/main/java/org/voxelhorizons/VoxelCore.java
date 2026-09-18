package org.voxelhorizons;

import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.voxelhorizons.command.CommandFactory;
import org.voxelhorizons.command.CommandRegistry;
import org.voxelhorizons.command.RootCommand;
import org.voxelhorizons.command.commands.AdminCommand;
import org.voxelhorizons.command.commands.BaseCommand;
import org.voxelhorizons.config.ConfigMigrationSupport;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.action.ActionDefinition;
import org.voxelhorizons.content.action.ActionType;
import org.voxelhorizons.content.action.EventActions;
import org.voxelhorizons.content.block.BlockAllocationRegistry;
import org.voxelhorizons.content.block.BlockAllocationStore;
import org.voxelhorizons.content.block.BlockDefinition;
import org.voxelhorizons.content.block.BlockDefinitionRegistry;
import org.voxelhorizons.content.load.ContentLoadException;
import org.voxelhorizons.content.load.ContentDefinitions;
import org.voxelhorizons.content.load.ContentLoader;
import org.voxelhorizons.content.render.RenderAllocationRegistry;
import org.voxelhorizons.content.render.RenderAllocationStore;
import org.voxelhorizons.content.runtime.ContentReloadResult;
import org.voxelhorizons.content.runtime.ContentRuntime;
import org.voxelhorizons.content.runtime.ContentRuntimeReloader;
import org.voxelhorizons.content.runtime.ContentSnapshot;
import org.voxelhorizons.content.runtime.ContentSnapshotValidator;
import org.voxelhorizons.item.ItemManager;
import org.voxelhorizons.integration.shopgui.ShopGuiPlusIntegration;
import org.voxelhorizons.block.BlockManager;
import org.voxelhorizons.block.BlockListener;
import org.voxelhorizons.action.ActionExecutor;
import org.voxelhorizons.action.ItemActionListener;
import org.voxelhorizons.pack.PackManager;
import org.voxelhorizons.platform.VersionAdapter;
import org.voxelhorizons.platform.VersionAdapterFactory;
import org.voxelhorizons.platform.server.ServerPlatformCapabilities;
import org.voxelhorizons.platform.server.ServerPlatformCapabilitiesFactory;
import org.voxelhorizons.text.ChatPlaceholderListener;
import org.voxelhorizons.text.InventoryTitlePlaceholderListener;
import org.voxelhorizons.text.PaperChatPlaceholderBridge;
import org.voxelhorizons.text.PlaceholderApiIntegration;
import org.voxelhorizons.text.PlayerListPlaceholderSynchronizer;
import org.voxelhorizons.text.TextPlaceholderService;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class VoxelCore extends JavaPlugin {

    public static VoxelCore instance;
    public Logger logger;

    private File configFile;
    public FileConfiguration config;

    private VersionAdapter versionAdapter;
    private ServerPlatformCapabilities serverPlatformCapabilities;
    private ContentLoader contentLoader;
    private ContentRuntime contentRuntime;
    private ContentRuntimeReloader contentReloader;
    private ItemManager itemManager;
    private BlockManager blockManager;
    private ActionExecutor actionExecutor;
    private PackManager packManager;
    private TextPlaceholderService textPlaceholderService;
    private Path contentRoot;

    public static VoxelCore getInstance() { return instance; }

    public VoxelCore() {
        if (instance != null) throw new IllegalStateException(getName() + " already initialized!");
        instance = this;
    }

    @Override
    public void onEnable() {
        logger = getLogger();

        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                logger.info("No Configuration File Found. Generating A New One...");
                saveDefaultConfig();
            }
            checkConfigVersion();
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Unable to initialize or migrate VoxelCore configuration", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        config = getConfig();

        try {
            serverPlatformCapabilities = ServerPlatformCapabilitiesFactory.create(getServer());
            versionAdapter = VersionAdapterFactory.create(this);
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "VoxelCore does not support this Minecraft platform", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        contentLoader = new ContentLoader();
        contentRoot = getDataFolder().toPath().resolve("content");

        try {
            Files.createDirectories(contentRoot);
            ContentDefinitions initialDefinitions = contentLoader.loadDefinitions(contentRoot);
            ItemDefinitionRegistry initialRegistry = initialDefinitions.items();
            RenderAllocationStore allocationStore = new RenderAllocationStore(getDataFolder().toPath().resolve("render-allocations.yml"));
            RenderAllocationRegistry initialAllocations = RenderAllocationRegistry.reconcile(initialRegistry, allocationStore.load());
            boolean modernBlockStates = versionAdapter.version().atLeast(1, 13, 0);
            BlockAllocationStore blockAllocationStore = new BlockAllocationStore(
                    getDataFolder().toPath().resolve("block-allocations.yml"));
            BlockAllocationRegistry initialBlockAllocations = BlockAllocationRegistry.reconcile(
                    initialDefinitions.blocks(), blockAllocationStore.load(), modernBlockStates);
            validateForPlatform(initialRegistry, initialAllocations);
            validateBlocks(initialDefinitions.blocks(), initialBlockAllocations, initialRegistry);
            allocationStore.save(initialAllocations);
            blockAllocationStore.save(initialBlockAllocations);
            contentRuntime = new ContentRuntime(new ContentSnapshot(1L, initialRegistry, initialAllocations,
                    initialDefinitions.blocks(), initialBlockAllocations));
            contentReloader = new ContentRuntimeReloader(contentLoader, contentRoot, contentRuntime, allocationStore,
                    new ContentSnapshotValidator() {
                        @Override public void validate(ItemDefinitionRegistry items, RenderAllocationRegistry allocations) {
                            validateForPlatform(items, allocations);
                        }
                        @Override public void validateBlocks(BlockDefinitionRegistry blocks,
                                                             BlockAllocationRegistry allocations) {
                            VoxelCore.this.validateBlocks(blocks, allocations, contentLoader.load(contentRoot));
                        }
                    }, blockAllocationStore, modernBlockStates);
            packManager = new PackManager(getDataFolder().toPath(), contentRoot, versionAdapter.version());
            textPlaceholderService = new TextPlaceholderService(packManager.uiGlyphs(true));
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Unable to create VoxelCore content directory " + contentRoot, exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        } catch (ContentLoadException exception) {
            logger.log(Level.SEVERE, "VoxelCore content failed to load; plugin startup aborted. " + exception.getMessage(), exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "VoxelCore content failed platform validation; plugin startup aborted. " + exception.getMessage(), exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        itemManager = new ItemManager(contentRuntime, versionAdapter);
        blockManager = new BlockManager(contentRuntime, versionAdapter.version().atLeast(1, 13, 0));
        actionExecutor = new ActionExecutor(blockManager, itemManager);
        if (getServer().getPluginManager().getPlugin("ShopGUIPlus") != null) {
            ShopGuiPlusIntegration.register(this, itemManager);
        }
        getServer().getPluginManager().registerEvents(new ItemActionListener(itemManager, actionExecutor), this);
        getServer().getPluginManager().registerEvents(new BlockListener(blockManager, itemManager, actionExecutor), this);
        if (packManager.currentTarget().supportsUiFonts()) {
            getServer().getPluginManager().registerEvents(new ChatPlaceholderListener(textPlaceholderService), this);
            PaperChatPlaceholderBridge.registerIfAvailable(this, textPlaceholderService);
            getServer().getPluginManager().registerEvents(
                    new InventoryTitlePlaceholderListener(this, textPlaceholderService), this);
            new PlayerListPlaceholderSynchronizer(this, textPlaceholderService).start();
            PlaceholderApiIntegration.registerIfAvailable(this, textPlaceholderService);
        }

        logger.info("VoxelCore platform ready on " + serverPlatformCapabilities.platformName()
                + " for Minecraft " + versionAdapter.version()
                + " with content revision " + contentRuntime.current().revision()
                + " (" + contentRuntime.current().items().size() + " items, "
                + contentRuntime.current().blocks().size() + " blocks)");

        RootCommand baseCommand = new BaseCommand();
        CommandFactory commandFactory = new CommandFactory(baseCommand);
        commandFactory.register(new AdminCommand());

        try {
            PluginCommand bukkitCommand = getCommand(baseCommand.getName());
            if (bukkitCommand == null) throw new IllegalStateException("Command '" + baseCommand.getName() + "' is missing from plugin.yml");
            bukkitCommand.setExecutor(commandFactory);
            bukkitCommand.setTabCompleter(commandFactory);
            CommandRegistry.register(commandFactory);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Unable to register VoxelCore commands", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        logger.info("VOXELCORE_READY revision=" + contentRuntime.current().revision()
                + " items=" + contentRuntime.current().items().size()
                + " platform=" + versionAdapter.version()
                + " server=" + serverPlatformCapabilities.platformName());
    }

    @Override
    public void onDisable() {
        if (config == null || configFile == null) return;
        try { config.save(configFile); }
        catch (IOException exception) { logger.log(Level.SEVERE, "Unable to save VoxelCore configuration", exception); }
    }

    public ContentReloadResult onReload() {
        if (contentReloader == null || contentRuntime == null || packManager == null) {
            throw new IllegalStateException("VoxelCore content runtime is not initialized");
        }
        try {
            packManager.uiGlyphs(false);
        } catch (RuntimeException exception) {
            ContentReloadResult failure = ContentReloadResult.failure(contentRuntime.current(),
                    "UI content validation failed: " + exception.getMessage());
            logger.warning("Content reload failed; revision " + failure.activeRevision() + " remains active. " + failure.message());
            return failure;
        }
        ContentReloadResult result = contentReloader.reload();
        if (result.success()) {
            textPlaceholderService.update(packManager.uiGlyphs(true));
            logger.info("Published content revision " + result.activeRevision() + " (" + result.itemCount()
                    + " items, " + result.blockCount() + " blocks)");
        } else {
            logger.warning("Content reload failed; revision " + result.activeRevision() + " remains active. " + result.message());
        }
        return result;
    }

    public VersionAdapter getVersionAdapter() { return versionAdapter; }
    public ServerPlatformCapabilities getServerPlatformCapabilities() { return serverPlatformCapabilities; }
    public ContentRuntime getContentRuntime() { return contentRuntime; }
    public ItemDefinitionRegistry getItemRegistry() { return contentRuntime.current().items(); }
    public ItemManager getItemManager() { return itemManager; }
    public BlockManager getBlockManager() { return blockManager; }
    public BlockDefinitionRegistry getBlockRegistry() { return contentRuntime.current().blocks(); }
    public PackManager getPackManager() { return packManager; }
    public TextPlaceholderService getTextPlaceholderService() { return textPlaceholderService; }

    private void validateForPlatform(ItemDefinitionRegistry items, RenderAllocationRegistry allocations) {
        for (ItemDefinition definition : items.entries().values()) {
            if (definition.abstractDefinition()) continue;
            versionAdapter.items().validateDefinition(definition, allocations.get(definition.id()).orElse(null));
        }
    }

    private void validateBlocks(BlockDefinitionRegistry blocks, BlockAllocationRegistry allocations,
                                ItemDefinitionRegistry items) {
        for (BlockDefinition definition : blocks.entries().values()) {
            if (definition.abstractDefinition()) continue;
            if (!allocations.get(definition.id()).isPresent()) {
                throw new IllegalArgumentException("Missing block allocation for " + definition.id());
            }
            if (definition.dropWhenMined() && definition.dropItem() != null
                    && !items.contains(definition.dropItem())) {
                throw new IllegalArgumentException("Block " + definition.id() + " drops unknown item "
                        + definition.dropItem());
            }
            validateActions(definition.id(), definition.events(), blocks, items);
        }
        for (ItemDefinition definition : items.entries().values()) {
            validateActions(definition.id(), definition.events(), blocks, items);
        }
    }

    private static void validateActions(org.voxelhorizons.content.ContentID owner, EventActions events,
                                        BlockDefinitionRegistry blocks, ItemDefinitionRegistry items) {
        for (java.util.List<ActionDefinition> actions : events.entries().values()) {
            for (ActionDefinition action : actions) {
                if (action.type() == ActionType.SET_BLOCK) {
                    org.voxelhorizons.content.ContentID target = org.voxelhorizons.content.ContentID.parse(
                            action.string("block"), owner.namespace());
                    if (!blocks.contains(target)) throw new IllegalArgumentException(owner
                            + " set_block references unknown block " + target);
                } else if (action.type() == ActionType.GIVE_ITEM || action.type() == ActionType.DROP_ITEM) {
                    org.voxelhorizons.content.ContentID target = org.voxelhorizons.content.ContentID.parse(
                            action.string("item"), owner.namespace());
                    if (!items.contains(target)) throw new IllegalArgumentException(owner
                            + " action references unknown item " + target);
                }
            }
        }
    }

    private void migrateConfig(int defaultVersion) {
        FileConfiguration current = getConfig();
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(getResource("config.yml"), StandardCharsets.UTF_8));
        backupConfig();
        ConfigMigrationSupport.merge(current, defaults, defaultVersion);
        try { current.save(configFile); }
        catch (IOException exception) { throw new IllegalStateException("Unable to save migrated config", exception); }
        reloadConfig();
    }

    private void backupConfig() {
        if (configFile == null || !configFile.isFile()) return;
        File backup = new File(getDataFolder(), "config.yml.old");
        int suffix = 1;
        while (backup.exists()) backup = new File(getDataFolder(), "config.yml.old." + suffix++);
        try { Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES); }
        catch (IOException exception) { throw new IllegalStateException("Unable to back up existing config to " + backup, exception); }
    }

    private void checkConfigVersion() {
        int currentVersion = getConfig().getInt("version", -1);
        int defaultVersion = getDefaultConfigVersion();
        if (defaultVersion < 0) throw new IllegalStateException("Bundled config.yml has no valid version");
        if (currentVersion == -1) {
            logger.warning("Config version missing; preserving existing values and adding current defaults.");
            migrateConfig(defaultVersion);
        } else if (currentVersion != defaultVersion) {
            logger.warning("Config schema change detected (v" + currentVersion + " → v" + defaultVersion + "); preserving user values.");
            migrateConfig(defaultVersion);
        }
    }

    private int getDefaultConfigVersion() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(getResource("config.yml"), StandardCharsets.UTF_8)).getInt("version", -1);
    }
}
