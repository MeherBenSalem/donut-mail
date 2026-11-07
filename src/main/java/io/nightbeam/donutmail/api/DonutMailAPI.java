package io.nightbeam.donutmail.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * DonutMail for other plugins. Add {@code softdepend: [DonutMail]} to your plugin.yml.
 *
 * <p>Every method that saves something returns a future that completes once it is stored (off the main
 * thread); it completes exceptionally when it could not be saved. Mail reaches offline players too, and
 * players on other servers when the servers share a MySQL database.
 */
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

    /** True while DonutMail is enabled. */
    public static boolean isAvailable() {
        return bridge != null;
    }

    // ── 2.0 ─────────────────────────────────────────────────────────────────

    /** Delivers items, money, commands and/or a voucher to one player's mailbox. Returns the mail id. */
    public static CompletableFuture<UUID> deliver(Delivery delivery) {
        return bridge().deliver(delivery);
    }

    /**
     * A parcel from one player to another, as if they had sent it: it can be cash on delivery
     * ({@code codPrice > 0}, needs Vault), and the recipient's block list and refuse toggle apply.
     * Anti-spam limits and postage do not. The items must already be out of any inventory.
     */
    public static CompletableFuture<ParcelResult> sendParcel(UUID from, String fromName, UUID to, List<ItemStack> items, String note,
                                                             double codPrice) {
        return bridge().sendParcel(from, fromName, to, items, note, codPrice);
    }

    /** Creates a campaign: one mail for every player, claimed once each. Returns its id. */
    public static CompletableFuture<UUID> createCampaign(CampaignRequest request) {
        return bridge().createCampaign(request);
    }

    /** Ends a campaign early; players who did not claim it no longer see it. */
    public static CompletableFuture<Boolean> endCampaign(String name) {
        return bridge().endCampaign(name);
    }

    /** New mail of an online player (0 when offline or not loaded yet). */
    public static int unread(UUID player) {
        return bridge == null ? 0 : bridge.unread(player);
    }

    /** Everything waiting in an online player's mailbox. */
    public static int waiting(UUID player) {
        return bridge == null ? 0 : bridge.waiting(player);
    }

    public static void openMailbox(Player player) {
        bridge().openMailbox(player);
    }

    // ── 1.x, unchanged ──────────────────────────────────────────────────────

    public static CompletableFuture<UUID> sendMail(UUID player, String message, ItemStack[] items) {
        return sendMail(player, "API", message, items, null);
    }

    public static CompletableFuture<UUID> sendMail(UUID player, String sender, String message, ItemStack[] items, String voucherId) {
        return bridge().sendMail(player, sender, message, items, voucherId);
    }

    public static CompletableFuture<UUID> sendVoucher(UUID player, String voucherId) {
        return bridge().sendVoucher(player, "API", voucherId);
    }

    private static DonutMailAPIBridge bridge() {
        DonutMailAPIBridge current = bridge;
        if (current == null) {
            throw new IllegalStateException("DonutMail API is not available. Is the plugin enabled?");
        }
        return current;
    }
}
