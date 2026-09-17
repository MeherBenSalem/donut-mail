package io.nightbeam.donutmail.util;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class InventoryUtil {
    private InventoryUtil() {
    }

    public static boolean canFitAll(Inventory inventory, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }

        int emptySlots = 0;
        Map<Integer, Integer> freeBySlot = new HashMap<>();

        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack current = contents[i];
            if (current == null || current.getType().isAir()) {
                emptySlots++;
                continue;
            }
            freeBySlot.put(i, current.getMaxStackSize() - current.getAmount());
        }

        for (ItemStack incoming : items) {
            if (incoming == null || incoming.getType().isAir()) {
                continue;
            }

            int remaining = incoming.getAmount();
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack existing = contents[i];
                if (existing == null || existing.getType().isAir()) {
                    continue;
                }
                if (!existing.isSimilar(incoming)) {
                    continue;
                }
                int free = freeBySlot.getOrDefault(i, 0);
                if (free <= 0) {
                    continue;
                }
                int moved = Math.min(free, remaining);
                remaining -= moved;
                freeBySlot.put(i, free - moved);
            }

            while (remaining > 0 && emptySlots > 0) {
                remaining -= Math.min(remaining, incoming.getMaxStackSize());
                emptySlots--;
            }

            if (remaining > 0) {
                return false;
            }
        }

        return true;
    }
}
