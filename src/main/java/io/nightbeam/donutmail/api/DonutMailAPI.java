package io.nightbeam.donutmail.api;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class DonutMailAPI {
    private static volatile DonutMailAPIBridge bridge;

    private DonutMailAPI() {
    }

    public static void registerBridge(DonutMailAPIBridge apiBridge) {
        bridge = apiBridge;
    }

    public static void clearBridge() {
        bridge = null;
    }

    public static CompletableFuture<UUID> sendMail(UUID player, String message, ItemStack[] items) {
        return sendMail(player, "API", message, items, null);
    }

    public static CompletableFuture<UUID> sendMail(UUID player, String sender, String message, ItemStack[] items, String voucherId) {
        ensureLoaded();
        return bridge.sendMail(player, sender, message, items, voucherId);
    }

    public static CompletableFuture<UUID> sendVoucher(UUID player, String voucherId) {
        ensureLoaded();
        return bridge.sendVoucher(player, "API", voucherId);
    }

    private static void ensureLoaded() {
        if (bridge == null) {
            throw new IllegalStateException("DonutMail API is not available. Is the plugin enabled?");
        }
    }
}
