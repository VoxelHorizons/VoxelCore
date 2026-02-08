package org.voxelhorizons.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class CommandFactory implements CommandExecutor, TabCompleter {

    private final RootCommand root;
    private final Map<String, SubCommand> subCommands = new HashMap<>();

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
        if(args.length == 0) {

            // Check if User Can Execute the Command
            if(!canExecute(sender, root.getPermission(), root.playerOnly())) {
                return true;
            }

            root.execute(sender, args);
            return true;
        }

        return subExecute(sender, args, subCommands);
    }

    private boolean subExecute(CommandSender sender, String[] args, Map<String, SubCommand> commands) {

        // Identify SubCommand
        SubCommand current = commands.get(args[0].toLowerCase());
        if(current == null) {
            sender.sendMessage("Unknown command.");
            return true;
        }

        // Check if Can Execute SubCommand
        if(!canExecute(sender, current.getPermission(), current.playerOnly())) {
            return true;
        }

        // Check if Executing Sub-SubCommand
        String[] remaining = java.util.Arrays.copyOfRange(args, 1, args.length);
        if(!current.getChildren().isEmpty() && remaining.length > 0) {
            return subExecute(sender, remaining, current.getChildren());
        }

        current.execute(sender, remaining);
        return true;
    }

    private boolean canExecute(CommandSender sender, String permission, boolean playerOnly) {

        // Check if Running from Console & Command is Player Only
        if(playerOnly && !(sender instanceof Player)) {
            sender.sendMessage("Only player can use this command.");
            return false;
        }

        // Check for Player Permissions
        if(permission != null && !sender.hasPermission(permission)) {
            sender.sendMessage("You do not have permission to use this command.");
            return false;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            return List.of();
        }

        return tabCompleteSub(sender, args, subCommands);
    }

    private List<String> tabCompleteSub(CommandSender sender, String[] args, Map<String, SubCommand> commands) {

        // Completing current level
        if (args.length == 1) {
            String input = args[0].toLowerCase();

            return commands.values().stream()
                    .distinct()
                    .filter(cmd -> canExecute(sender, cmd.getPermission(), cmd.playerOnly()))
                    .map(SubCommand::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .sorted()
                    .toList();
        }

        // Traverse deeper
        SubCommand current = commands.get(args[0].toLowerCase());
        if (current == null) {
            return List.of();
        }

        if (!canExecute(sender, current.getPermission(), current.playerOnly())) {
            return List.of();
        }

        return tabCompleteSub(
                sender,
                java.util.Arrays.copyOfRange(args, 1, args.length),
                current.getChildren()
        );
    }

    public Set<String> getAllCommandPaths() {
        Set<String> paths = new HashSet<>();
        collectPaths(root.getName(), subCommands, paths);
        return paths;
    }

    private void collectPaths(String prefix, Map<String, SubCommand> commands, Set<String> out) {
        for (SubCommand cmd : new HashSet<>(commands.values())) {
            String path = prefix + " " + cmd.getName();
            out.add(path);

            if (!cmd.getChildren().isEmpty()) {
                collectPaths(path, cmd.getChildren(), out);
            }
        }
    }
}
