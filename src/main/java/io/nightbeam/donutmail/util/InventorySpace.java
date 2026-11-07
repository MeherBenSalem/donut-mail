package io.nightbeam.donutmail.util;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Whole-stack delivery: a stack is handed over only when all of it fits. The plan is computed on a copy of
 * the inventory and applied in one step, so what was planned is exactly what the player gets.
 */
public final class InventorySpace {
    private InventorySpace() {
    }

    /**
     * @param contents the player's storage contents (not changed)
     * @param items    items to deliver, in order; null entries (unreadable items) never fit
     */
    public static Plan plan(ItemStack[] contents, List<ItemStack> items) {
        ItemStack[] slots = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            slots[i] = contents[i] == null || contents[i].getType().isAir() ? null : contents[i].clone();
        }
        List<Integer> delivered = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            ItemStack item = items.get(index);
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            ItemStack[] attempt = slots.clone();
            for (int i = 0; i < attempt.length; i++) {
                if (attempt[i] != null) attempt[i] = attempt[i].clone();
            }
            if (fit(attempt, item)) {
                slots = attempt;
                delivered.add(index);
            }
        }
        return new Plan(slots, delivered);
    }

    private static boolean fit(ItemStack[] slots, ItemStack item) {
        int remaining = item.getAmount();
        int max = Math.max(1, item.getMaxStackSize());
        for (ItemStack slot : slots) {
            if (remaining <= 0) break;
            if (slot != null && slot.isSimilar(item) && slot.getAmount() < max) {
                int moved = Math.min(max - slot.getAmount(), remaining);
                slot.setAmount(slot.getAmount() + moved);
                remaining -= moved;
            }
        }
        for (int i = 0; i < slots.length && remaining > 0; i++) {
            if (slots[i] == null) {
                ItemStack placed = item.clone();
                int moved = Math.min(max, remaining);
                placed.setAmount(moved);
                slots[i] = placed;
                remaining -= moved;
            }
        }
        return remaining <= 0;
    }

    /** The inventory after delivery and the indexes of the items that fit. */
    public record Plan(ItemStack[] contents, List<Integer> delivered) {
    }
}
