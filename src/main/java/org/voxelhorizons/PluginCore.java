package org.voxelhorizons;

import org.bukkit.plugin.java.JavaPlugin;
import org.voxelhorizons.debug.CIMode;

public final class PluginCore extends JavaPlugin {

    public static PluginCore instance;
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
    }

    @Override
    public void onDisable() {
    }
}
