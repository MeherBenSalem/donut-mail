package io.nightbeam.donutmail.service;

import io.nightbeam.donutmail.model.VoucherDefinition;
import io.nightbeam.donutmail.util.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

/** Item specs from config files ("DIAMOND:3") and console commands run as rewards. */
public final class Rewards {
    private Rewards() {
    }

    /** "MATERIAL[:amount]" entries as stored items, split into full stacks. Unknown materials are logged and skipped. */
    public static List<ItemStack> items(List<String> specs, Logger logger, String where) {
        List<ItemStack> out = new ArrayList<>();
        if (specs == null) return out;
        for (String spec : specs) {
            if (spec == null || spec.isBlank()) continue;
            String[] parts = spec.trim().split("[: ]+");
            Material material = Material.matchMaterial(parts[0].toUpperCase(Locale.ROOT));
            if (material == null || material.isAir() || !material.isItem()) {
                logger.warning(where + ": unknown item '" + spec + "' was skipped.");
                continue;
            }
            int amount = 1;
            if (parts.length > 1) {
                try {
                    amount = Math.max(1, Integer.parseInt(parts[1]));
                } catch (NumberFormatException invalid) {
                    logger.warning(where + ": bad amount in '" + spec + "', using 1.");
                }
            }
            out.addAll(stacks(material, amount));
        }
        return out;
    }

    public static List<ItemStack> stacks(Material material, int amount) {
        List<ItemStack> out = new ArrayList<>();
        int max = Math.max(1, material.getMaxStackSize());
        int left = Math.min(amount, 64 * 54);
        while (left > 0) {
            int size = Math.min(max, left);
            out.add(new ItemStack(material, size));
            left -= size;
        }
        return out;
    }

    public static List<String> encoded(List<String> specs, Logger logger, String where) {
        return ItemSerializer.serializeAll(items(specs, logger, where));
    }

    /** Replaces %player%, %player_name%, %name% and %uuid% (any case). */
    public static String fill(String command, String playerName, UUID uuid) {
        String name = playerName == null ? "" : playerName;
        return command
                .replaceAll("(?i)%player%", name)
                .replaceAll("(?i)%player_name%", name)
                .replaceAll("(?i)%name%", name)
                .replaceAll("(?i)%uuid%", uuid == null ? "" : uuid.toString());
    }

    /** Dispatches commands as the console. Call on the global thread. */
    public static void dispatch(List<String> commands, String playerName, UUID uuid, Logger logger) {
        if (commands == null) return;
        for (String command : commands) {
            if (command == null) continue;
            String line = fill(command.trim(), playerName, uuid);
            if (line.startsWith("/")) line = line.substring(1);
            if (line.isEmpty()) continue;
            try {
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line)) {
                    logger.warning("Reward command did not run: " + line);
                }
            } catch (RuntimeException failure) {
                logger.warning("Reward command failed: " + line + " (" + failure.getMessage() + ")");
            }
        }
    }

    public static List<String> voucherCommands(VoucherDefinition voucher) {
        return voucher == null ? List.of() : voucher.commands();
    }
}
