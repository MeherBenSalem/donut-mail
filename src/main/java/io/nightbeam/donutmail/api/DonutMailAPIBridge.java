package io.nightbeam.donutmail.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Implemented by the plugin; use {@link DonutMailAPI}. */
public interface DonutMailAPIBridge {
    CompletableFuture<UUID> sendMail(UUID player, String sender, String message, ItemStack[] items, String voucherId);

    CompletableFuture<UUID> sendVoucher(UUID player, String sender, String voucherId);

    CompletableFuture<UUID> deliver(Delivery delivery);

    CompletableFuture<ParcelResult> sendParcel(UUID from, String fromName, UUID to, List<ItemStack> items, String note, double codPrice);

    CompletableFuture<UUID> createCampaign(CampaignRequest request);

    CompletableFuture<Boolean> endCampaign(String name);

    int unread(UUID player);

    int waiting(UUID player);

    void openMailbox(Player player);
}
