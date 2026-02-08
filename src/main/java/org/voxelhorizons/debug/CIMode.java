package org.voxelhorizons.debug;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class CIMode {

    private final JavaPlugin plugin;

    public CIMode(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!Boolean.getBoolean("ci.mode")) {
            return; // Only run in CI mode
        }

        plugin.getLogger().info("CI mode detected, scheduling automated shutdown...");

        // Test Demo Commands
        CommandSender console = Bukkit.getConsoleSender();
        try {
            Bukkit.dispatchCommand(console, "plugincore");
            Bukkit.dispatchCommand(console, "plugincore admin");
            Bukkit.dispatchCommand(console, "plugincore admin reload");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Schedule task 10 seconds after startup
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getLogger().info("CI mode: running automated test commands...");

            // Example: print help command
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "help");

            plugin.getLogger().info("CI mode: shutting down server.");

            // Clean shutdown
            Bukkit.getServer().shutdown();

            // Force JVM exit after a short delay
            new Thread(() -> {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                System.exit(0);
            }).start();

        }, 20L * 10); // 10 seconds delay (20 ticks = 1 second)
    }
}