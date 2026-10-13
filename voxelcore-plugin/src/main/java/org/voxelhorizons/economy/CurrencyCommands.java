package org.voxelhorizons.economy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.milkbowl.vault2.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;

/**
 * Handles currency-qualified commands only. Unqualified Essentials commands
 * remain untouched, including /money, /balance, /pay and /baltop.
 */
public final class CurrencyCommands implements Listener {
    private final VoxelCurrencyBridge bridge;

    public CurrencyCommands(VoxelCurrencyBridge bridge) {
        this.bridge = bridge;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String line = event.getMessage().substring(1);
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 2) return;
        String root = parts[0].toLowerCase(Locale.ROOT);
        int offset;
        String action;
        if (root.equals("money")) {
            if (parts.length < 3) return;
            action = parts[1].toLowerCase(Locale.ROOT);
            offset = 2;
        } else if (root.equals("bal") || root.equals("balance")) {
            action = "balance";
            offset = 1;
        } else if (root.equals("pay")) {
            action = "pay";
            offset = 1;
        } else if (root.equals("baltop")) {
            action = "top";
            offset = 1;
        } else return;

        if (action.equals("bal")) action = "balance";
        if (action.equals("baltop")) action = "top";
        if (!action.equals("balance") && !action.equals("pay") && !action.equals("top")) return;
        String currency = parts[parts.length - 1].toLowerCase(Locale.ROOT);
        // Only override commands explicitly naming a VoxelCore currency.
        if (!bridge.supports(currency)) return;
        Player sender = event.getPlayer();
        event.setCancelled(true);
        if (action.equals("balance")) {
            if (parts.length != offset + 1) {
                sender.sendMessage("§cUsage: /money balance <currency>");
                return;
            }
            sender.sendMessage("§aBalance: §f" + bridge.balance(sender.getUniqueId(), currency).toPlainString()
                    + " " + currency);
            return;
        }
        if (action.equals("pay")) {
            if (parts.length != offset + 3) {
                sender.sendMessage("§cUsage: /money pay <player> <amount> <currency>");
                return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(parts[offset]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage("§cThat player has not joined the server.");
                return;
            }
            if (target.getUniqueId().equals(sender.getUniqueId())) {
                sender.sendMessage("§cYou cannot pay yourself.");
                return;
            }
            BigDecimal amount;
            try {
                amount = new BigDecimal(parts[offset + 1]);
                if (amount.signum() <= 0 || amount.scale() > 0) throw new NumberFormatException();
            } catch (NumberFormatException exception) {
                sender.sendMessage("§cEnter a positive whole-number amount.");
                return;
            }
            EconomyResponse withdrawn = bridge.transaction("withdraw", sender.getUniqueId(), currency, amount);
            if (!withdrawn.transactionSuccess()) {
                sender.sendMessage("§cUnable to pay: " + withdrawn.errorMessage);
                return;
            }
            EconomyResponse deposited = bridge.transaction("deposit", target.getUniqueId(), currency, amount);
            if (!deposited.transactionSuccess()) {
                bridge.transaction("deposit", sender.getUniqueId(), currency, amount);
                sender.sendMessage("§cPayment failed; refund attempted.");
                return;
            }
            sender.sendMessage("§aPaid " + amount.toPlainString() + " " + currency + " to " + target.getName());
            if (target.isOnline() && target.getPlayer() != null) {
                target.getPlayer().sendMessage("§aReceived " + amount.toPlainString() + " " + currency + " from " + sender.getName());
            }
            return;
        }
        if (parts.length != offset + 1) {
            sender.sendMessage("§cUsage: /money top <currency>");
            return;
        }
        List<Map.Entry<UUID, BigDecimal>> ranking =
                new ArrayList<Map.Entry<UUID, BigDecimal>>(bridge.top(currency).entrySet());
        ranking.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        sender.sendMessage("§6Top balances — " + currency);
        for (int i = 0; i < Math.min(10, ranking.size()); i++) {
            Map.Entry<UUID, BigDecimal> row = ranking.get(i);
            String name = Bukkit.getOfflinePlayer(row.getKey()).getName();
            sender.sendMessage("§e" + (i + 1) + ". §f" + (name == null ? row.getKey() : name)
                    + "§7: §a" + row.getValue().toPlainString());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTabComplete(TabCompleteEvent event) {
        String[] parts = event.getBuffer().trim().split("\\s+");
        if (parts.length < 2) return;
        String root = parts[0].replace("/", "").toLowerCase(Locale.ROOT);
        if (!Arrays.asList("money", "bal", "balance", "pay", "baltop").contains(root)) return;
        String previous = parts[parts.length - 1].toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<String>(event.getCompletions());
        for (String currency : bridge.currencies()) {
            if (currency.startsWith(previous) && !completions.contains(currency)) completions.add(currency);
        }
        event.setCompletions(completions);
    }
}
