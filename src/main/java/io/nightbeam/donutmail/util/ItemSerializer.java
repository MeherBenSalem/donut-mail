package io.nightbeam.donutmail.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Items are stored as Paper's binary item format ({@code nbt1:} + Base64). It records the Minecraft data
 * version and runs Mojang's data fixers when read, so stored mail survives Minecraft upgrades. Items
 * written by PostMaster (Bukkit object streams, no prefix) are still read.
 */
public final class ItemSerializer {
    public static final String NBT_PREFIX = "nbt1:";

    private ItemSerializer() {
    }

    public static String serialize(ItemStack item) {
        if (item == null) {
            throw new IllegalArgumentException("item");
        }
        return NBT_PREFIX + Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public static ItemStack deserialize(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("encoded");
        }
        if (encoded.startsWith(NBT_PREFIX)) {
            byte[] bytes = Base64.getDecoder().decode(encoded.substring(NBT_PREFIX.length()));
            return ItemStack.deserializeBytes(bytes);
        }
        return deserializeLegacy(encoded);
    }

    /** Decodes an item, or returns null when it cannot be read on this server (for example a newer version's item). */
    public static ItemStack tryDeserialize(String encoded) {
        try {
            ItemStack item = deserialize(encoded);
            return item == null || item.getType().isAir() ? null : item;
        } catch (RuntimeException | LinkageError unreadable) {
            return null;
        }
    }

    private static ItemStack deserializeLegacy(String encoded) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            Object obj = dataInput.readObject();
            return (ItemStack) obj;
        } catch (IOException | ClassNotFoundException | ClassCastException ex) {
            throw new IllegalStateException("Failed to deserialize item", ex);
        }
    }

    public static List<String> serializeAll(List<ItemStack> items) {
        List<String> out = new ArrayList<>();
        if (items == null) return out;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                out.add(serialize(item));
            }
        }
        return out;
    }

    /** 1.x stored a list joined with ';'. Kept for tools that still produce it. */
    public static String serializeList(List<ItemStack> items) {
        return String.join(";", serializeAll(items));
    }

    public static List<ItemStack> deserializeList(String encodedList) {
        if (encodedList == null || encodedList.isBlank()) {
            return List.of();
        }
        List<ItemStack> items = new ArrayList<>();
        for (String chunk : encodedList.split(";")) {
            if (!chunk.isBlank()) {
                items.add(deserialize(chunk));
            }
        }
        return items;
    }
}
