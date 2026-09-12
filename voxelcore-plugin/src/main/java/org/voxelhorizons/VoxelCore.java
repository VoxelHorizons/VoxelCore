package org.voxelhorizons;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.voxelhorizons.command.CommandFactory;
import org.voxelhorizons.command.CommandRegistry;
import org.voxelhorizons.command.RootCommand;
import org.voxelhorizons.command.commands.AdminCommand;
import org.voxelhorizons.command.commands.BaseCommand;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.content.item.ItemDefinitionRegistry;
import org.voxelhorizons.item.ItemManager;
import org.voxelhorizons.platform.VersionAdapter;
import org.voxelhorizons.platform.VersionAdapterFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.logging.Logger;

public final class VoxelCore extends JavaPlugin {

    public static VoxelCore instance;
    public Logger logger;

    private File configFile;
    public FileConfiguration config;

    private VersionAdapter versionAdapter;
    private ItemDefinitionRegistry itemRegistry;
    private ItemManager itemManager;

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
            exception.printStackTrace();
        }

        checkConfigVersion();
        config = getConfig();

        versionAdapter = VersionAdapterFactory.create(this);
        itemRegistry = new ItemDefinitionRegistry(Collections.<org.voxelhorizons.content.ContentID, ItemDefinition>emptyMap());
        itemManager = new ItemManager(itemRegistry, versionAdapter);

        logger.info("VoxelCore platform ready for Minecraft " + versionAdapter.version());

        RootCommand baseCommand = new BaseCommand();
        CommandFactory commandFactory = new CommandFactory(baseCommand);
        commandFactory.register(new AdminCommand());

        try {
            CommandRegistry.register(commandFactory);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        if (config == null || configFile == null) {
            return;
        }

        try {
            config.save(configFile);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public void onReload() {
    }

    public VersionAdapter getVersionAdapter() {
        return versionAdapter;
    }

    public ItemDefinitionRegistry getItemRegistry() {
        return itemRegistry;
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
