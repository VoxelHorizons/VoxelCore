package org.voxelhorizons.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CommandFactory implements CommandExecutor, TabCompleter {

    private final RootCommand root;
    private final Map<String, SubCommand> subCommands = new HashMap<String, SubCommand>();

    public CommandFactory(RootCommand root) {
        this.root = root;
    }

    public void register(SubCommand command) {
        subCommands.put(command.getName().toLowerCase(), command);
        for (String alias : command.getAliases()) {
            subCommands.put(alias.toLowerCase(), command);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!canExecute(sender, root.getPermission(), root.playerOnly())) {
                return true;
            }
            root.execute(sender, args);
            return true;
        }

        return subExecute(sender, args, subCommands);
    }

    private boolean subExecute(CommandSender sender, String[] args, Map<String, SubCommand> commands) {
        SubCommand current = commands.get(args[0].toLowerCase());
        if (current == null) {
            sender.sendMessage("Unknown command.");
            return true;
        }

        if (!canExecute(sender, current.getPermission(), current.playerOnly())) {
            return true;
        }

        String[] remaining = Arrays.copyOfRange(args, 1, args.length);
        if (!current.getChildren().isEmpty() && remaining.length > 0) {
            return subExecute(sender, remaining, current.getChildren());
        }

        current.execute(sender, remaining);
        return true;
    }

    private boolean canExecute(CommandSender sender, String permission, boolean playerOnly) {
        if (playerOnly && !(sender instanceof Player)) {
            sender.sendMessage("Only player can use this command.");
            return false;
        }

        if (permission != null && !sender.hasPermission(permission)) {
            sender.sendMessage("You do not have permission to use this command.");
            return false;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }

        return tabCompleteSub(sender, args, subCommands);
    }

    private List<String> tabCompleteSub(CommandSender sender, String[] args, Map<String, SubCommand> commands) {
        if (args.length == 1) {
            final String input = args[0].toLowerCase();
            return commands.values().stream()
                    .distinct()
                    .filter(cmd -> canExecute(sender, cmd.getPermission(), cmd.playerOnly()))
                    .map(SubCommand::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .sorted()
                    .collect(Collectors.toList());
        }

        SubCommand current = commands.get(args[0].toLowerCase());
        if (current == null || !canExecute(sender, current.getPermission(), current.playerOnly())) {
            return Collections.emptyList();
        }

        String[] remaining = Arrays.copyOfRange(args, 1, args.length);
        if (current.getChildren().isEmpty()) {
            return current.onTabComplete(sender, remaining);
        }
        return tabCompleteSub(sender, remaining, current.getChildren());
    }

    public Set<String> getAllCommandPaths() {
        Set<String> paths = new HashSet<String>();
        collectPaths(root.getName(), subCommands, paths);
        return paths;
    }

    private void collectPaths(String prefix, Map<String, SubCommand> commands, Set<String> out) {
        for (SubCommand cmd : new HashSet<SubCommand>(commands.values())) {
            String path = prefix + " " + cmd.getName();
            out.add(path);

            if (!cmd.getChildren().isEmpty()) {
                collectPaths(path, cmd.getChildren(), out);
            }
        }
    }
}
