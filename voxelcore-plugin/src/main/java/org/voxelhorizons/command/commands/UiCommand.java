package org.voxelhorizons.command.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.command.SubCommand;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.pack.UiGlyphDefinition;
import org.voxelhorizons.pack.UiGlyphRegistry;
import org.voxelhorizons.platform.Version;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Administrative discovery and clipboard helpers for compiled UI glyphs. */
public final class UiCommand implements SubCommand {
    private final Map<String, SubCommand> children = new HashMap<String, SubCommand>();

    public UiCommand() {
        register(new ListCommand());
        register(new InfoCommand());
        register(new CopyCommand());
    }

    private void register(SubCommand command) {
        children.put(command.getName(), command);
        for (String alias : command.getAliases()) children.put(alias, command);
    }

    @Override public String getName() { return "ui"; }
    @Override public List<String> getAliases() { return Collections.singletonList("glyph"); }
    @Override public String getPermission() { return "voxelcore.admin.ui"; }
    @Override public boolean playerOnly() { return false; }
    @Override public Map<String, SubCommand> getChildren() { return children; }
    @Override public void execute(CommandSender sender, String[] args) {
        sender.sendMessage("Usage: /voxelcore admin ui <list|info|copy> [content-id]");
    }

    private static UiGlyphRegistry registry(boolean persist) {
        return VoxelCore.getInstance().getPackManager().uiGlyphs(persist);
    }

    private static ContentID parse(String value) {
        return ContentID.parse(value, "voxelhorizons");
    }

    private static List<String> ids(String prefix) {
        final String normalized = prefix == null ? "" : prefix.toLowerCase(java.util.Locale.ROOT);
        UiGlyphRegistry registry = registry(false);
        List<String> suggestions = new ArrayList<String>();
        for (ContentID id : registry.entries().keySet()) {
            String suggestion = normalized.startsWith(":") ? ":" + id.value() + ":" : id.toString();
            if (suggestion.startsWith(normalized) && !suggestions.contains(suggestion)) suggestions.add(suggestion);
        }
        Collections.sort(suggestions);
        return suggestions;
    }

    private static UiGlyphDefinition find(CommandSender sender, String[] args, boolean persist) {
        if (args.length != 1) {
            sender.sendMessage("A UI ContentID is required.");
            return null;
        }
        String requested = args[0];
        if (requested.startsWith(":") && requested.endsWith(":") && requested.length() > 2) {
            requested = requested.substring(1, requested.length() - 1);
            UiGlyphDefinition alias = null;
            for (UiGlyphDefinition candidate : registry(persist).entries().values()) {
                if (candidate.id().value().equals(requested)) {
                    if (alias != null) {
                        sender.sendMessage("Ambiguous UI alias ':" + requested + ":'. Use a full ContentID.");
                        return null;
                    }
                    alias = candidate;
                }
            }
            if (alias != null) return alias;
        }
        final ContentID id;
        try { id = parse(requested); }
        catch (IllegalArgumentException exception) {
            sender.sendMessage("Invalid UI ContentID '" + args[0] + "'.");
            return null;
        }
        UiGlyphDefinition glyph = registry(persist).get(id).orElse(null);
        if (glyph == null) sender.sendMessage("Unknown UI glyph '" + id + "'.");
        return glyph;
    }

    private static final class ListCommand implements SubCommand {
        @Override public String getName() { return "list"; }
        @Override public List<String> getAliases() { return Collections.emptyList(); }
        @Override public String getPermission() { return "voxelcore.admin.ui.list"; }
        @Override public boolean playerOnly() { return false; }
        @Override public void execute(CommandSender sender, String[] args) {
            List<UiGlyphDefinition> values = new ArrayList<UiGlyphDefinition>(registry(false).entries().values());
            Collections.sort(values, new Comparator<UiGlyphDefinition>() {
                @Override public int compare(UiGlyphDefinition left, UiGlyphDefinition right) {
                    return left.id().toString().compareTo(right.id().toString());
                }
            });
            sender.sendMessage("VoxelCore UI glyphs (" + values.size() + "): " + values);
        }
    }

    private static final class InfoCommand implements SubCommand {
        @Override public String getName() { return "info"; }
        @Override public List<String> getAliases() { return Collections.emptyList(); }
        @Override public String getPermission() { return "voxelcore.admin.ui.info"; }
        @Override public boolean playerOnly() { return false; }
        @Override public List<String> onTabComplete(CommandSender sender, String[] args) {
            return args.length == 1 ? ids(args[0]) : Collections.<String>emptyList();
        }
        @Override public void execute(CommandSender sender, String[] args) {
            UiGlyphDefinition glyph = find(sender, args, false);
            if (glyph == null) return;
            sender.sendMessage("VoxelCore UI " + glyph.id() + ": texture=" + glyph.texture()
                    + ", alias=:" + glyph.id().value() + ":, scale_ratio=" + glyph.scaleRatio()
                    + ", y_position=" + glyph.yPosition()
                    + ", advance=" + glyph.advance() + ", gui=" + glyph.gui()
                    + ", character=" + glyph.character() + " (" + glyph.escapedCodePoint() + ")");
        }
    }

    private static final class CopyCommand implements SubCommand {
        @Override public String getName() { return "copy"; }
        @Override public List<String> getAliases() { return Collections.singletonList("character"); }
        @Override public String getPermission() { return "voxelcore.admin.ui.copy"; }
        @Override public boolean playerOnly() { return false; }
        @Override public List<String> onTabComplete(CommandSender sender, String[] args) {
            return args.length == 1 ? ids(args[0]) : Collections.<String>emptyList();
        }
        @Override public void execute(CommandSender sender, String[] args) {
            UiGlyphDefinition glyph = find(sender, args, true);
            if (glyph == null) return;
            String escaped = String.format("\\u%04X", glyph.codePoint());
            if (sender instanceof Player && VoxelCore.getInstance().getVersionAdapter().version()
                    .compareTo(Version.of(1, 15, 0)) >= 0) {
                Player player = (Player) sender;
                String json = "{\"text\":\"[Copy " + glyph.id() + " " + glyph.escapedCodePoint()
                        + "]\",\"color\":\"aqua\",\"underlined\":true,\"clickEvent\":{\"action\":\"copy_to_clipboard\",\"value\":\""
                        + glyph.character() + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to copy the UI character\"}}";
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " " + json);
            } else {
                sender.sendMessage("UI character for " + glyph.id() + ": " + glyph.character());
            }
            sender.sendMessage("Codepoint: " + glyph.escapedCodePoint() + " | escaped: " + escaped);
        }
    }
}
