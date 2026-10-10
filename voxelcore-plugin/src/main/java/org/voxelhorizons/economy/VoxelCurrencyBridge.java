package org.voxelhorizons.economy;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault2.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Optional VaultUnlocked (Vault2) provider. All currencies are independently owned
 * by VoxelCore, and Essentials remains the legacy Vault Coins provider.
 */
public final class VoxelCurrencyBridge implements InvocationHandler {
    private final JavaPlugin plugin;

    private final Map<String, Integer> currencies = new LinkedHashMap<String, Integer>();
    private final Map<UUID, String> names = new HashMap<UUID, String>();
    private final File balancesFile;
    private final YamlConfiguration balances;
    private final String defaultCurrency;
    private Economy service;

    private VoxelCurrencyBridge(JavaPlugin plugin, File balancesFile, String defaultCurrency) {
        this.plugin = plugin;

        this.balancesFile = balancesFile;
        this.balances = YamlConfiguration.loadConfiguration(balancesFile);
        this.defaultCurrency = defaultCurrency;
    }

    public static VoxelCurrencyBridge start(JavaPlugin plugin) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("economy");
        if (section == null || !section.getBoolean("enabled", false)) return null;
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            plugin.getLogger().warning("Voxel currencies require VaultUnlocked; integration not started.");
            return null;
        }
        // Avoid replacing an existing Vault2 provider. VaultUnlocked publishes one
        // economy service at a time; multiple providers cannot be safely combined.
        if (Bukkit.getServicesManager().getRegistration(Economy.class) != null) {
            plugin.getLogger().warning("Another Vault2 economy is registered. VoxelCore will not override it.");
            return null;
        }
        String main = section.getString("default-currency", "tokens").toLowerCase(Locale.ROOT);
        VoxelCurrencyBridge bridge = new VoxelCurrencyBridge(plugin,
                new File(plugin.getDataFolder(), "currency-balances.yml"), main);
        ConfigurationSection configured = section.getConfigurationSection("currencies");
        if (configured == null || configured.getKeys(false).isEmpty()) {
            plugin.getLogger().warning("No additional currencies configured.");
            return null;
        }
        for (String raw : configured.getKeys(false)) {
            String currency = raw.toLowerCase(Locale.ROOT);
            if (!currency.matches("[a-z0-9_-]{1,32}")) {
                plugin.getLogger().warning("Ignoring invalid or reserved currency: " + raw);
                continue;
            }
            int decimals = configured.getInt(raw + ".decimals", 0);
            if (decimals < 0 || decimals > 6) {
                plugin.getLogger().warning("Invalid currency precision for " + raw);
                continue;
            }
            bridge.currencies.put(currency, decimals);
        }
        if (!bridge.currencies.containsKey(main)) {
            plugin.getLogger().warning("The default currency must appear in economy.currencies: " + main);
            return null;
        }
        bridge.service = (Economy) Proxy.newProxyInstance(Economy.class.getClassLoader(),
                new Class<?>[]{Economy.class}, bridge);
        Bukkit.getServicesManager().register(Economy.class, bridge.service, plugin, ServicePriority.Normal);
        plugin.getLogger().info("Registered VaultUnlocked currencies: " + bridge.currencies.keySet());
        return bridge;
    }

    public boolean supports(String currency) { return currencies.containsKey(currency.toLowerCase(Locale.ROOT)); }
    public Collection<String> currencies() { return Collections.unmodifiableCollection(currencies.keySet()); }
    public synchronized BigDecimal balance(UUID id, String currency) { return read(id, currency.toLowerCase(Locale.ROOT)); }
    public synchronized net.milkbowl.vault2.economy.EconomyResponse transaction(String operation, UUID id, String currency, BigDecimal amount) {
        return modify(operation, id, currency.toLowerCase(Locale.ROOT), amount);
    }
    public synchronized Map<UUID, BigDecimal> top(String currency) {
        Map<UUID, BigDecimal> result = new HashMap<UUID, BigDecimal>();
        ConfigurationSection section = balances.getConfigurationSection("balances");
        if (section != null) for (String key : section.getKeys(false)) {
            try { UUID id = UUID.fromString(key); result.put(id, read(id, currency)); }
            catch (IllegalArgumentException ignored) { }
        }
        return result;
    }
    public void shutdown() {
        if (service != null) Bukkit.getServicesManager().unregister(Economy.class, service);
    }

    private String currency(Object[] args) {
        if (args == null) return defaultCurrency;
        // Vault2 currency-aware balance/deposit/withdraw methods use the fifth
        // parameter for currency, following plugin name, UUID, world and amount.
        if (args.length >= 5 && args[3] instanceof String) return ((String) args[3]).toLowerCase(Locale.ROOT);
        if (args.length == 3 && args[2] instanceof String
                && !(args[1] instanceof UUID)) return ((String)args[2]).toLowerCase(Locale.ROOT);
        return defaultCurrency;
    }

    private BigDecimal read(UUID id, String currency) {
        return new BigDecimal(balances.getString("balances." + id + "." + currency, "0"));
    }

    private BigDecimal rounded(BigDecimal amount, String currency) {
        return amount.setScale(currencies.get(currency), RoundingMode.HALF_UP);
    }

    private net.milkbowl.vault2.economy.EconomyResponse response(BigDecimal amount, BigDecimal balance,
                      net.milkbowl.vault2.economy.EconomyResponse.ResponseType type, String error) {
        return new net.milkbowl.vault2.economy.EconomyResponse(amount, balance, type, error);
    }

    private net.milkbowl.vault2.economy.EconomyResponse modify(String operation, UUID id,
                                                                String currency, BigDecimal amount) {
        if (!currencies.containsKey(currency) || amount == null || amount.signum() < 0
                || amount.scale() > currencies.getOrDefault(currency, 0)) {
            return response(BigDecimal.ZERO, BigDecimal.ZERO,
                    net.milkbowl.vault2.economy.EconomyResponse.ResponseType.FAILURE, "Invalid currency or amount");
        }
        BigDecimal current = read(id, currency);
        BigDecimal next = operation.equals("deposit") ? current.add(amount) : current.subtract(amount);
        if (next.signum() < 0) return response(BigDecimal.ZERO, current,
                net.milkbowl.vault2.economy.EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        balances.set("balances." + id + "." + currency, next.toPlainString());
        try {
            balances.save(balancesFile);
        } catch (IOException ex) {
            balances.set("balances." + id + "." + currency, current.toPlainString());
            plugin.getLogger().log(Level.SEVERE, "Unable to save currency transaction", ex);
            return response(BigDecimal.ZERO, current,
                    net.milkbowl.vault2.economy.EconomyResponse.ResponseType.FAILURE, "Persistence failed");
        }
        return response(amount, next, net.milkbowl.vault2.economy.EconomyResponse.ResponseType.SUCCESS, "");
    }

    @Override
    public synchronized Object invoke(Object proxy, Method method, Object[] args) {
        String name = method.getName();
        if (name.equals("toString")) return "VoxelCore VaultUnlocked currencies";
        if (name.equals("hashCode")) return System.identityHashCode(proxy);
        if (name.equals("equals")) return proxy == args[0];
        if (name.equals("getName")) return "VoxelCoreCurrencies";
        if (name.equals("isEnabled") || name.equals("hasMultiCurrencySupport")) return true;
        if (name.equals("hasSharedAccountSupport") || name.equals("supportsAsync")) return false;
        if (name.equals("async")) return java.util.Optional.empty();
        if (name.equals("currencies")) return Collections.unmodifiableCollection(new ArrayList<String>(currencies.keySet()));
        if (name.equals("hasCurrency")) return currencies.containsKey(((String)args[0]).toLowerCase(Locale.ROOT));
        if (name.equals("getDefaultCurrency")) return defaultCurrency;
        if (name.equals("defaultCurrencyNameSingular")) return defaultCurrency;
        if (name.equals("defaultCurrencyNamePlural")) return defaultCurrency;
        if (name.equals("fractionalDigits")) return args.length > 1 ? currencies.getOrDefault(String.valueOf(args[1]).toLowerCase(Locale.ROOT), -1) : currencies.get(defaultCurrency);
        if (name.equals("format")) {
            BigDecimal value = null;
            for (Object arg : args) if (arg instanceof BigDecimal) value = (BigDecimal)arg;
            return value == null ? "" : value.toPlainString();
        }
        if (name.equals("getUUIDNameMap")) return Collections.unmodifiableMap(names);
        if (name.equals("getAccountName")) return java.util.Optional.ofNullable(names.get((UUID)args[0]));
        if (name.equals("createAccount")) {
            UUID id = (UUID)args[0];
            names.put(id, (String)args[1]);
            return true;
        }
        if (name.equals("hasAccount")) {
            UUID id = (UUID)args[0];
            return balances.contains("balances." + id);
        }
        if (name.equals("renameAccount")) {
            UUID id = (UUID)args[args.length - 2];
            names.put(id, (String)args[args.length - 1]);
            return true;
        }
        if (name.equals("deleteAccount")) return false;
        if (name.equals("accountSupportsCurrency")) return currencies.containsKey(((String)args[2]).toLowerCase(Locale.ROOT));
        if (name.equals("getBalance") || name.equals("balance")) {
            String currency = args.length >= 4 ? String.valueOf(args[3]).toLowerCase(Locale.ROOT) : defaultCurrency;
            return currencies.containsKey(currency) ? read((UUID)args[1], currency) : BigDecimal.ZERO;
        }
        if (name.equals("has")) {
            String currency = args.length >= 5 ? String.valueOf(args[3]).toLowerCase(Locale.ROOT) : defaultCurrency;
            BigDecimal amount = (BigDecimal)args[args.length - 1];
            return currencies.containsKey(currency) && read((UUID)args[1], currency).compareTo(amount) >= 0;
        }
        if (name.equals("deposit") || name.equals("withdraw")) {
            String currency = args.length >= 5 ? String.valueOf(args[3]).toLowerCase(Locale.ROOT) : defaultCurrency;
            return modify(name, (UUID)args[1], currency, (BigDecimal)args[args.length - 1]);
        }
        if (name.equals("set")) {
            String currency = args.length >= 5 ? String.valueOf(args[3]).toLowerCase(Locale.ROOT) : defaultCurrency;
            UUID id = (UUID)args[1];
            BigDecimal amount = (BigDecimal)args[args.length - 1];
            if (!currencies.containsKey(currency)) return response(BigDecimal.ZERO, BigDecimal.ZERO,
                    net.milkbowl.vault2.economy.EconomyResponse.ResponseType.FAILURE, "Unknown currency");
            BigDecimal delta = amount.subtract(read(id, currency));
            return modify(delta.signum() >= 0 ? "deposit" : "withdraw", id, currency, delta.abs());
        }
        if (name.equals("canDeposit") || name.equals("canWithdraw")) {
            return response(BigDecimal.ZERO, BigDecimal.ZERO,
                    net.milkbowl.vault2.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
        }
        // Shared-account functions intentionally unsupported; don't claim ownership.
        if (method.getReturnType() == boolean.class) return false;
        if (Collection.class.isAssignableFrom(method.getReturnType())) return Collections.emptyList();
        if (method.getReturnType() == java.util.Optional.class) return java.util.Optional.empty();
        if (method.getReturnType() == net.milkbowl.vault2.economy.EconomyResponse.class)
            return response(BigDecimal.ZERO, BigDecimal.ZERO,
                    net.milkbowl.vault2.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Unsupported operation");
        throw new UnsupportedOperationException("Unsupported VaultUnlocked call: " + name);
    }
}
