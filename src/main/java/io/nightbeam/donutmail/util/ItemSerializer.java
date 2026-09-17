package io.nightbeam.donutmail.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class ItemSerializer {
    private static final String NBT_PREFIX = "nbt1:";

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

    private static ItemStack deserializeLegacy(String encoded) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            Object obj = dataInput.readObject();
            return (ItemStack) obj;
        } catch (IOException | ClassNotFoundException ex) {
            throw new IllegalStateException("Failed to deserialize item", ex);
        }
    }

    public static String serializeList(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        List<String> out = new ArrayList<>();
        for (ItemStack item : items) {
            out.add(serialize(item));
        }
        return String.join(";", out);
    }

    public static List<ItemStack> deserializeList(String encodedList) {
        if (encodedList == null || encodedList.isBlank()) {
            return List.of();
        }
        String[] chunks = encodedList.split(";");
        List<ItemStack> items = new ArrayList<>(chunks.length);
        for (String chunk : chunks) {
            if (!chunk.isBlank()) {
                items.add(deserialize(chunk));
            }
        }
        return items;
    }
}
