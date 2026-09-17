package io.nightbeam.donutmail.service;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class TargetResolver {
    public Set<UUID> resolveTargets(String token) {
        Set<UUID> targets = new HashSet<>();
        if (token == null || token.isBlank()) {
            return targets;
        }

        String normalized = token.trim().toLowerCase();
        if (normalized.equals("allonline")) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                targets.add(online.getUniqueId());
            }
            return targets;
        }

        if (normalized.equals("all")) {
            for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
                if (offline.getUniqueId() != null) {
                    targets.add(offline.getUniqueId());
                }
            }
            return targets;
        }

        if (token.contains(";")) {
            for (String split : token.split(";")) {
                addPlayer(split.trim(), targets);
            }
            return targets;
        }

        addPlayer(token.trim(), targets);
        return targets;
    }

    private void addPlayer(String input, Set<UUID> targets) {
        if (input.isBlank()) {
            return;
        }
        try {
            UUID asUuid = UUID.fromString(input);
            targets.add(asUuid);
            return;
        } catch (IllegalArgumentException ignored) {
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(input);
        if (offline.getUniqueId() != null) {
            targets.add(offline.getUniqueId());
        }
    }
}
