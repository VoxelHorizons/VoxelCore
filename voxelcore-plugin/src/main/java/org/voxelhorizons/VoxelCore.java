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
import org.voxelhorizons.content.load.ContentLoadException;
import org.voxelhorizons.content.load.ContentLoader;
import org.voxelhorizons.content.render.RenderAllocationRegistry;
import org.voxelhorizons.content.render.RenderAllocationStore;
import org.voxelhorizons.content.runtime.ContentReloadResult;
import org.voxelhorizons.content.runtime.ContentRuntime;
import org.voxelhorizons.content.runtime.ContentRuntimeReloader;
import org.voxelhorizons.content.runtime.ContentSnapshot;
import org.voxelhorizons.content.runtime.ContentSnapshotValidator;
import org.voxelhorizons.item.ItemManager;
import org.voxelhorizons.pack.PackManager;
import org.voxelhorizons.platform.VersionAdapter;
import org.voxelhorizons.platform.VersionAdapterFactory;

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
    private ContentLoader contentLoader;
    private ContentRuntime contentRuntime;
    private ContentRuntimeReloader contentReloader;
    private ItemManager itemManager;
    private PackManager packManager;
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
            ItemDefinitionRegistry initialRegistry = contentLoader.load(contentRoot);
            RenderAllocationStore allocationStore = new RenderAllocationStore(getDataFolder().toPath().resolve("render-allocations.yml"));
            RenderAllocationRegistry initialAllocations = RenderAllocationRegistry.reconcile(initialRegistry, allocationStore.load());
            validateForPlatform(initialRegistry, initialAllocations);
            allocationStore.save(initialAllocations);
            contentRuntime = new ContentRuntime(new ContentSnapshot(1L, initialRegistry, initialAllocations));
            contentReloader = new ContentRuntimeReloader(contentLoader, contentRoot, contentRuntime, allocationStore,
                    new ContentSnapshotValidator() {
                        @Override public void validate(ItemDefinitionRegistry items, RenderAllocationRegistry allocations) {
                            validateForPlatform(items, allocations);
                        }
                    });
            packManager = new PackManager(getDataFolder().toPath(), contentRoot, versionAdapter.version());
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

        logger.info("VoxelCore platform ready for Minecraft " + versionAdapter.version()
                + " with content revision " + contentRuntime.current().revision()
                + " (" + contentRuntime.current().items().size() + " items)");

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
                + " platform=" + versionAdapter.version());
    }

    @Override
    public void onDisable() {
        if (config == null || configFile == null) return;
        try { config.save(configFile); }
        catch (IOException exception) { logger.log(Level.SEVERE, "Unable to save VoxelCore configuration", exception); }
    }

    public ContentReloadResult onReload() {
        if (contentReloader == null || contentRuntime == null) throw new IllegalStateException("VoxelCore content runtime is not initialized");
        ContentReloadResult result = contentReloader.reload();
        if (result.success()) logger.info("Published content revision " + result.activeRevision() + " (" + result.itemCount() + " items)");
        else logger.warning("Content reload failed; revision " + result.activeRevision() + " remains active. " + result.message());
        return result;
    }

    public VersionAdapter getVersionAdapter() { return versionAdapter; }
    public ContentRuntime getContentRuntime() { return contentRuntime; }
    public ItemDefinitionRegistry getItemRegistry() { return contentRuntime.current().items(); }
    public ItemManager getItemManager() { return itemManager; }
    public PackManager getPackManager() { return packManager; }

    private void validateForPlatform(ItemDefinitionRegistry items, RenderAllocationRegistry allocations) {
        for (ItemDefinition definition : items.entries().values()) {
            versionAdapter.items().validateDefinition(definition, allocations.get(definition.id()).orElse(null));
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
