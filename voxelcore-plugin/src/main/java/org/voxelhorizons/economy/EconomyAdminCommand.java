package org.voxelhorizons.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.voxelhorizons.economy.api.CurrencyDefinition;
import org.voxelhorizons.economy.api.EconomyReceipt;
import org.voxelhorizons.economy.api.EconomyService;
import org.voxelhorizons.economy.api.EconomyTransaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Explicitly privileged test and administration interface; never grants funds to ordinary players. */
public final class EconomyAdminCommand implements CommandExecutor, TabCompleter {
    private final EconomyService economy;
    public EconomyAdminCommand(EconomyService economy) { this.economy = economy; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("voxelcore.admin.economy")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }
        if (economy == null) {
            sender.sendMessage(ChatColor.RED + "VoxelCore economy is disabled; enable economy.enabled and restart.");
            return true;
        }
        if (args.length == 1 && "currencies".equalsIgnoreCase(args[0])) {
            for (CurrencyDefinition currency : economy.currencies()) {
                sender.sendMessage(ChatColor.GOLD + currency.id() + ChatColor.GRAY + " (" + currency.name()
                        + ", precision " + currency.precision() + ")");
            }
            return true;
        }
        if (args.length < 3) { help(sender); return true; }
        try {
            String operation = args[0].toLowerCase(Locale.ROOT);
            UUID account = onlineOrUuid(args[1]);
            String currencyId;
            if ("balance".equals(operation) && args.length == 3) {
                currencyId = args[2];
                sender.sendMessage(ChatColor.GREEN + currencyId + ": "
                        + economy.balance(account, currencyId).toPlainString());
                return true;
            }
            if ("transfer".equals(operation) && args.length == 5) {
                UUID recipient = onlineOrUuid(args[2]);
                currencyId = args[3];
                BigDecimal amount = positiveAmount(args[4]);
                run(sender, EconomyTransaction.builder(UUID.randomUUID(), "voxelcore.admin")
                        .transfer(account, recipient, currencyId, amount).build());
                return true;
            }
            if (("credit".equals(operation) || "debit".equals(operation)) && args.length == 4) {
                currencyId = args[2];
                BigDecimal amount = positiveAmount(args[3]);
                if ("debit".equals(operation)) amount = amount.negate();
                run(sender, EconomyTransaction.builder(UUID.randomUUID(), "voxelcore.admin")
                        .adjust(account, currencyId, amount).build());
                return true;
            }
        } catch (RuntimeException exception) {
            sender.sendMessage(ChatColor.RED + "Economy operation rejected: " + exception.getMessage());
            return true;
        }
        help(sender);
        return true;
    }

    private void run(CommandSender sender, EconomyTransaction transaction) {
        EconomyReceipt receipt = economy.execute(transaction);
        sender.sendMessage((receipt.successful() ? ChatColor.GREEN : ChatColor.RED)
                + "Economy " + receipt.status() + "; transaction " + receipt.id());
        // Changes are audited in SQLite by the ledger itself; also log privileged adjustments.
        Bukkit.getLogger().info("[VoxelCore Economy] " + sender.getName() + " executed "
                + receipt.status() + " transaction " + receipt.id());
    }

    private static UUID onlineOrUuid(String nameOrUuid) {
        try { return UUID.fromString(nameOrUuid); }
        catch (IllegalArgumentException ignored) { }
        Player online = Bukkit.getPlayerExact(nameOrUuid);
        if (online == null) throw new IllegalArgumentException(
                "Player must be online or supply their full UUID: " + nameOrUuid);
        return online.getUniqueId();
    }

    private static BigDecimal positiveAmount(String raw) {
        BigDecimal amount = new BigDecimal(raw);
        if (amount.signum() <= 0) throw new IllegalArgumentException("Amount must be greater than zero");
        return amount;
    }

    private static void help(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "/vceconomy currencies");
        sender.sendMessage(ChatColor.YELLOW + "/vceconomy balance <online-player|uuid> <currency>");
        sender.sendMessage(ChatColor.YELLOW + "/vceconomy credit|debit <online-player|uuid> <currency> <amount>");
        sender.sendMessage(ChatColor.YELLOW + "/vceconomy transfer <from-player> <to-player> <currency> <amount>");
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("voxelcore.admin.economy")) return Collections.emptyList();
        if (args.length == 1) {
            List<String> choices = new ArrayList<String>();
            for (String value : new String[]{"currencies", "balance", "credit", "debit", "transfer"}) {
                if (value.startsWith(args[0].toLowerCase(Locale.ROOT))) choices.add(value);
            }
            return choices;
        }
        if (args.length == 3 && !"transfer".equalsIgnoreCase(args[0])
                || args.length == 4 && "transfer".equalsIgnoreCase(args[0])) {
            if (economy == null) return Collections.emptyList();
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            List<String> values = new ArrayList<String>();
            for (CurrencyDefinition definition : economy.currencies())
                if (definition.id().startsWith(prefix)) values.add(definition.id());
            return values;
        }
        return Collections.emptyList();
    }
}
