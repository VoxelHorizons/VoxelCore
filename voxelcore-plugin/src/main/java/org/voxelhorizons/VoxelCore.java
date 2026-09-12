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
import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.content.load.ContentLoadException;
import org.voxelhorizons.content.load.ContentLoader;
import org.voxelhorizons.content.runtime.ContentReloadResult;
import org.voxelhorizons.content.runtime.ContentRuntime;
import org.voxelhorizons.content.runtime.ContentRuntimeReloader;
import org.voxelhorizons.content.runtime.ContentSnapshot;
import org.voxelhorizons.item.ItemManager;
import org.voxelhorizons.platform.VersionAdapter;
import org.voxelhorizons.platform.VersionAdapterFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private Path contentRoot;

    public static VoxelCore getInstance() {
        return instance;
    }

    public VoxelCore() {
        if (instance != null) {
            throw new IllegalStateException(getName() + " already initialized!");
        }
        instance = this;
    }

    @Override
    public void onEnable() {
        logger = getLogger();

        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }

            configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                logger.info("No Configuration File Found. Generating A New One...");
                saveDefaultConfig();
            }
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Unable to initialize VoxelCore data folder", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        checkConfigVersion();
        config = getConfig();

        versionAdapter = VersionAdapterFactory.create(this);
        contentLoader = new ContentLoader();
        contentRoot = getDataFolder().toPath().resolve("content");

        try {
            Files.createDirectories(contentRoot);
            ItemDefinitionRegistry initialRegistry = contentLoader.load(contentRoot);
            contentRuntime = new ContentRuntime(new ContentSnapshot(1L, initialRegistry));
            contentReloader = new ContentRuntimeReloader(contentLoader, contentRoot, contentRuntime);
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Unable to create VoxelCore content directory " + contentRoot, exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        } catch (ContentLoadException exception) {
            logger.log(Level.SEVERE, "VoxelCore content failed to load; plugin startup aborted. " + exception.getMessage(), exception);
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
            if (bukkitCommand == null) {
                throw new IllegalStateException("Command '" + baseCommand.getName() + "' is missing from plugin.yml");
            }
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
        if (config == null || configFile == null) {
            return;
        }

        try {
            config.save(configFile);
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Unable to save VoxelCore configuration", exception);
        }
    }

    public ContentReloadResult onReload() {
        if (contentReloader == null || contentRuntime == null) {
            throw new IllegalStateException("VoxelCore content runtime is not initialized");
        }

        ContentReloadResult result = contentReloader.reload();
        if (result.success()) {
            logger.info("Published content revision " + result.activeRevision()
                    + " (" + result.itemCount() + " items)");
        } else {
            logger.warning("Content reload failed; revision " + result.activeRevision()
                    + " remains active. " + result.message());
        }
        return result;
    }

    public VersionAdapter getVersionAdapter() {
        return versionAdapter;
    }

    public ContentRuntime getContentRuntime() {
        return contentRuntime;
    }

    public ItemDefinitionRegistry getItemRegistry() {
        return contentRuntime.current().items();
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    private void replaceConfig() {
        File oldConfig = new File(getDataFolder(), "config.yml");
        File backup = new File(getDataFolder(), "config.yml.old");

        if (backup.exists()) {
            backup.delete();
        }

        if (oldConfig.exists()) {
            oldConfig.renameTo(backup);
        }

        saveDefaultConfig();
        reloadConfig();
    }

    private void checkConfigVersion() {
        int currentVersion = getConfig().getInt("version", -1);
        int defaultVersion = getDefaultConfigVersion();

        if (currentVersion == -1) {
            logger.warning("Config version missing! Regenerating config.");
            replaceConfig();
            return;
        }

        if (currentVersion != defaultVersion) {
            logger.warning("Outdated config detected (v" + currentVersion + " → v" + defaultVersion + ")");
            replaceConfig();
        }
    }

    private int getDefaultConfigVersion() {
        return YamlConfiguration.loadConfiguration(
                new InputStreamReader(getResource("config.yml"), StandardCharsets.UTF_8)
        ).getInt("version", -1);
    }
}
