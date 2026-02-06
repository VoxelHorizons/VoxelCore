package org.voxelhorizons;

import org.bukkit.plugin.java.JavaPlugin;
import org.voxelhorizons.debug.CIMode;

public final class PluginCore extends JavaPlugin {

    @Override
    public void onEnable() {
        // Instantiate and start CI-mode auto shutdown
        new CIMode(this).start();
    }

    @Override
    public void onDisable() {
    }
}
