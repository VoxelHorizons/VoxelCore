package org.voxelhorizons;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.voxelhorizons.command.CommandFactory;
import org.voxelhorizons.command.CommandRegistry;
import org.voxelhorizons.command.RootCommand;
import org.voxelhorizons.command.commands.AdminCommand;
import org.voxelhorizons.command.commands.BaseCommand;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public final class VoxelCore extends JavaPlugin {

    public static VoxelCore instance;
    public Logger logger;

    private File configFile;
    public FileConfiguration config;

    public static VoxelCore getInstance() {
        return instance;
    }

    public VoxelCore() {
        if(this.instance != null) {
            throw new IllegalStateException(this.getName() + " already initialized!");
        }
        this.instance = this;
    }

    @Override
    public void onEnable() {
        // Setup Logging
        this.logger = getLogger();

        // Plugin Directory & Configuration Setup
        try {

            // Create Plugin Directory if Not Exists Already
            if(!this.getDataFolder().exists()) {
                this.getDataFolder().mkdir();
            }

            // Copy Default Config if None Exists (Fresh Installations)
            this.configFile = new File(this.getDataFolder(), "config.yml");
            if(!this.configFile.exists()) {
                this.logger.info("No Configuration File Found. Generating A New One...");
                saveDefaultConfig();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        checkConfigVersion();
        this.config = this.getConfig();

        // Command Registry & Factories Setup
        RootCommand baseCommand = new BaseCommand();
        CommandFactory commandFactory = new CommandFactory(baseCommand);

        commandFactory.register(new AdminCommand());

        try {
            CommandRegistry.register(commandFactory);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {

        // Save Configuration File
        try {
            this.config.save(this.configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onReload() {

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
            logger.warning("Outdated config detected (v" + currentVersion +
                    " → v" + defaultVersion + ")");
            replaceConfig();
        }
    }

    private int getDefaultConfigVersion() {
        FileConfiguration defaultConfig =
                YamlConfiguration.loadConfiguration(
                        new InputStreamReader(
                                getResource("config.yml"),
                                StandardCharsets.UTF_8
                        )
                );
        return defaultConfig.getInt("version", -1);
    }
}
