package org.voxelhorizons;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.voxelhorizons.debug.CIMode;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public final class PluginCore extends JavaPlugin {

    public static PluginCore instance;
    public Logger logger;

    private File configFile;
    public FileConfiguration config;

    public static PluginCore getInstance() {
        return instance;
    }

    public PluginCore() {
        if(this.instance != null) {
            throw new IllegalStateException(getName() + " already initialized!");
        }
        this.instance = this;
    }

    @Override
    public void onEnable() {
        // Instantiate and start CI-mode auto shutdown
        // DO NOT REMOVE
        // This helps prevent the Github Action from getting stuck
        new CIMode(this).start();

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
        this.config = this.getConfig();
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
}
