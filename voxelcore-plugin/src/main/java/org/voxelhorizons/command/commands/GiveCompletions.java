package org.voxelhorizons.command.commands;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class GiveCompletions {
    private static final List<String> AMOUNTS = Arrays.asList("1", "16", "32", "64");

    private GiveCompletions() { }

    static List<String> complete(String[] args, Collection<String> ids) {
        if (args.length == 1) return matching(ids, args[0]);
        if (args.length == 2) return matching(AMOUNTS, args[1]);
        if (args.length == 3) {
            List<String> players = new ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) players.add(player.getName());
            return matching(players, args[2]);
        }
        return Collections.emptyList();
    }

    static List<String> matching(Collection<String> candidates, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> results = new ArrayList<String>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) results.add(candidate);
        }
        Collections.sort(results, String.CASE_INSENSITIVE_ORDER);
        return results;
    }
}
