package org.voxelhorizons.command.commands;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.milkbowl.vault2.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.command.SubCommand;
import org.voxelhorizons.economy.VoxelCurrencyBridge;

/** Administrative operations only on currencies owned by VoxelCore. */
public final class EconomyCommand implements SubCommand {
    private final Map<String, SubCommand> children = new HashMap<String, SubCommand>();

    public EconomyCommand() {
        for (String action : Arrays.asList("balance", "give", "take", "set", "reset")) {
            ActionCommand command = new ActionCommand(action);
            children.put(action, command);
        }
        children.put("bal", children.get("balance"));
    }

    @Override public String getName() { return "eco"; }
    @Override public List<String> getAliases() { return Arrays.asList("economy", "currency"); }
    @Override public String getPermission() { return "voxelcore.admin.eco"; }
    @Override public boolean playerOnly() { return false; }
    @Override public Map<String, SubCommand> getChildren() { return children; }
    @Override public void execute(CommandSender sender, String[] args) {
        sender.sendMessage("Usage: /vc admin eco <balance|give|take|set|reset> <player> [amount] <currency>");
    }

    private static VoxelCurrencyBridge provider(CommandSender sender) {
        VoxelCurrencyBridge bridge = VoxelCore.getInstance().getCurrencyBridge();
        if (bridge == null) sender.sendMessage("§cVoxelCore economy is disabled or has not registered with VaultUnlocked.");
        return bridge;
    }

    private static final class ActionCommand implements SubCommand {
        private final String operation;

        ActionCommand(String operation) { this.operation = operation; }
        @Override public String getName() { return operation; }
        @Override public List<String> getAliases() { return Collections.emptyList(); }
        @Override public String getPermission() { return "voxelcore.admin.eco." + operation; }
        @Override public boolean playerOnly() { return false; }

        @Override public List<String> onTabComplete(CommandSender sender, String[] args) {
            VoxelCurrencyBridge bridge = VoxelCore.getInstance().getCurrencyBridge();
            if (args.length == 1) {
                List<String> result = new ArrayList<String>();
                for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    result.add(p.getName());
                return result;
            }
            if (bridge == null) return Collections.emptyList();
            int currencyIndex = operation.equals("balance") || operation.equals("reset") ? 2 : 3;
            if (args.length != currencyIndex) return Collections.emptyList();
            String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<String>();
            for (String id : bridge.currencies()) if (id.startsWith(typed)) result.add(id);
            return result;
        }

        @Override public void execute(CommandSender sender, String[] args) {
            VoxelCurrencyBridge bridge = provider(sender);
            if (bridge == null) return;
            boolean amountRequired = !operation.equals("balance") && !operation.equals("reset");
            if (args.length != (amountRequired ? 3 : 2)) {
                sender.sendMessage("§cUsage: /vc admin eco " + operation
                        + (amountRequired ? " <player> <amount> <currency>" : " <player> <currency>"));
                return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage("§cThat player has never joined this server.");
                return;
            }
            String currency = args[args.length - 1].toLowerCase(Locale.ROOT);
            if (!bridge.supports(currency)) {
                sender.sendMessage("§cUnknown VoxelCore currency: " + currency
                        + ". Available: " + bridge.currencies());
                return;
            }
            BigDecimal before = bridge.balance(target.getUniqueId(), currency);
            if (operation.equals("balance")) {
                sender.sendMessage("§e" + target.getName() + "§7: §a" + before.toPlainString() + " " + currency);
                return;
            }

            BigDecimal amount = BigDecimal.ZERO;
            if (amountRequired) {
                try {
                    amount = new BigDecimal(args[1]);
                    if (amount.signum() < 0 || amount.scale() > 0 || amount.precision() > 18)
                        throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cAmount must be a non-negative whole number (up to 18 digits).");
                    return;
                }
            }
            BigDecimal desired = before;
            if (operation.equals("give")) desired = before.add(amount);
            else if (operation.equals("take")) desired = before.subtract(amount).max(BigDecimal.ZERO);
            else if (operation.equals("set")) desired = amount;

            BigDecimal delta = desired.subtract(before);
            EconomyResponse outcome;
            if (delta.signum() == 0) {
                sender.sendMessage("§eNo balance change for " + target.getName() + " (" + before.toPlainString() + " " + currency + ").");
                return;
            }
            outcome = bridge.transaction(delta.signum() > 0 ? "deposit" : "withdraw",
                    target.getUniqueId(), currency, delta.abs());
            if (!outcome.transactionSuccess()) {
                sender.sendMessage("§cEconomy operation failed: " + outcome.errorMessage);
                return;
            }
            sender.sendMessage("§a" + operation + " succeeded for " + target.getName()
                    + ": " + before.toPlainString() + " -> " + outcome.balance.toPlainString() + " " + currency);
            VoxelCore.getInstance().getLogger().info("[EconomyAdmin] " + sender.getName() + " " + operation
                    + " " + target.getUniqueId() + " " + currency + " before=" + before.toPlainString()
                    + " after=" + outcome.balance.toPlainString());
        }
    }
}
