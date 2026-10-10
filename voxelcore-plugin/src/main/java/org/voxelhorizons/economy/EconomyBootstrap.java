package org.voxelhorizons.economy;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.voxelhorizons.economy.api.CurrencyDefinition;
import org.voxelhorizons.economy.api.EconomyService;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Enables the new economy only when explicitly configured by an administrator. */
public final class EconomyBootstrap {
    private EconomyBootstrap() { }

    public static SqliteEconomyService start(JavaPlugin plugin) throws Exception {
        if (!plugin.getConfig().getBoolean("economy.enabled", false)) return null;
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("economy.currencies");
        if (section == null || section.getKeys(false).isEmpty())
            throw new IllegalArgumentException("economy.enabled requires configured economy.currencies");
        List<CurrencyDefinition> definitions = new ArrayList<CurrencyDefinition>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection value = section.getConfigurationSection(id);
            if (value == null) throw new IllegalArgumentException("Currency must be an object: " + id);
            String name = value.getString("name", id);
            int precision = value.getInt("precision", 2);
            BigDecimal maximum = new BigDecimal(value.getString("maximum-balance", "1000000000000"));
            definitions.add(new CurrencyDefinition(id, name, precision, maximum));
        }
        Path file = plugin.getDataFolder().toPath().resolve("economy").resolve("ledger.sqlite");
        SqliteEconomyService service = new SqliteEconomyService(file, definitions);
        try {
            plugin.getServer().getServicesManager().register(EconomyService.class, service, plugin, ServicePriority.Normal);
            return service;
        } catch (RuntimeException error) {
            service.close();
            throw error;
        }
    }
}
