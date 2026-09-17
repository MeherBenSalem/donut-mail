package io.nightbeam.donutmail.api;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * @deprecated Use {@link DonutMailAPI}. Kept for one release so dependents keep compiling.
 */
@Deprecated(since = "1.1.0", forRemoval = true)
public final class PostMasterAPI {
    private PostMasterAPI() {
    }

    public static CompletableFuture<UUID> sendMail(UUID player, String message, ItemStack[] items) {
        return DonutMailAPI.sendMail(player, message, items);
    }

    public static CompletableFuture<UUID> sendMail(UUID player, String sender, String message, ItemStack[] items, String voucherId) {
        return DonutMailAPI.sendMail(player, sender, message, items, voucherId);
    }

    public static CompletableFuture<UUID> sendVoucher(UUID player, String voucherId) {
        return DonutMailAPI.sendVoucher(player, voucherId);
    }
}
