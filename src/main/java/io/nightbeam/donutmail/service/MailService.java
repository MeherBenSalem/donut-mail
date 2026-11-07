package io.nightbeam.donutmail.service;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.api.event.MailClaimEvent;
import io.nightbeam.donutmail.api.event.ParcelPaidEvent;
import io.nightbeam.donutmail.api.event.ParcelReturnedEvent;
import io.nightbeam.donutmail.model.ClaimLock;
import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.MailState;
import io.nightbeam.donutmail.model.VoucherDefinition;
import io.nightbeam.donutmail.storage.MailStorage;
import io.nightbeam.donutmail.util.InventorySpace;
import io.nightbeam.donutmail.util.ItemSerializer;
import io.nightbeam.donutmail.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Claims, payments, declines, returns and expiry. Every step that changes a mail is one conditional update
 * in storage; the one that wins does the work, everyone else is told the mail is gone.
 *
 * <p>A claim is three steps: lock the mail (PENDING → CLAIMING with a token), hand the stacks that fit to
 * the player on their own thread while writing a receipt (token and delivered stacks) into the player's
 * save data in the same tick, then finish the claim (CLAIMED, or PENDING with what is left). If the server
 * stops in between, the next time the player joins this server the receipt decides: present means the
 * delivery was saved with the player, so the claim is finished; absent means it was not, so the mail goes
 * back to PENDING. Either way nothing is lost or duplicated.
 */
public final class MailService {
    private static final Set<MailState> RETURNABLE = EnumSet.of(MailState.PENDING, MailState.COD);

    private final DonutMailPlugin plugin;
    private final NamespacedKey receiptKey;
    /** Claim tokens this server is working on right now; anything else that is CLAIMING here was interrupted. */
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    /** Players with an action running, so double clicks do not start a second one. */
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

    public MailService(DonutMailPlugin plugin) {
        this.plugin = plugin;
        this.receiptKey = new NamespacedKey(plugin, "receipts");
    }

    private MailStorage storage() {
        return plugin.storage();
    }

    private long now() {
        return System.currentTimeMillis();
    }

    // ── Sending server mail ─────────────────────────────────────────────────

    /** A server mail to one player, with the configured expiry. */
    public MailRecord.Builder serverMail(MailKind kind, UUID receiver, String senderName) {
        long now = now();
        return MailRecord.builder().kind(kind).receiver(receiver).senderName(senderName).createdAt(now).updatedAt(now)
                .expiresAt(plugin.settings().serverMailExpiry(now)).state(MailState.PENDING);
    }

    /** Saves a mail and tells the receiver. */
    public CompletableFuture<MailRecord> deliver(MailRecord mail) {
        return plugin.scheduler().supply(() -> {
            storage().insert(mail);
            return mail;
        }).thenApply(saved -> {
            plugin.audit().log("DELIVER", "mail", saved.id(), "kind", saved.kind(), "to", saved.receiver(), "from", saved.senderName(),
                    "items", saved.items().size(), "money", saved.money(), "voucher", saved.voucherId(), "commands", saved.commands().size());
            plugin.notifier().mailArrived(saved);
            return saved;
        });
    }

    public CompletableFuture<List<MailRecord>> deliverAll(List<MailRecord> mails) {
        return plugin.scheduler().supply(() -> {
            storage().insertAll(mails);
            return mails;
        }).thenApply(saved -> {
            for (MailRecord mail : saved) {
                plugin.audit().log("DELIVER", "mail", mail.id(), "kind", mail.kind(), "to", mail.receiver(), "from", mail.senderName(),
                        "items", mail.items().size(), "money", mail.money(), "voucher", mail.voucherId());
                plugin.notifier().mailArrived(mail);
            }
            return saved;
        });
    }

    // ── Claiming ────────────────────────────────────────────────────────────

    public boolean isBusy(UUID player) {
        return busy.contains(player);
    }

    /** Claims one mail for an online player: everything that fits, the rest stays. */
    public CompletableFuture<ClaimResult> claim(Player player, UUID mailId) {
        UUID uuid = player.getUniqueId();
        return plugin.scheduler().supply(() -> storage().mail(mailId).orElse(null)).thenCompose(mail -> {
            if (mail == null || !mail.receiver().equals(uuid) || mail.state() == MailState.CLAIMING || !mail.state().isActive()) {
                return done(ClaimResult.Status.GONE);
            }
            if (mail.state() == MailState.COD) {
                return done(ClaimResult.Status.WAITING_PAYMENT);
            }
            if (mail.isExpired(now())) {
                return done(ClaimResult.Status.EXPIRED);
            }
            if (mail.money() > 0 && !plugin.economy().available()) {
                return done(ClaimResult.Status.NEEDS_ECONOMY);
            }
            if (mail.voucherId() != null && plugin.vouchers().getVoucher(mail.voucherId()) == null) {
                return done(ClaimResult.Status.VOUCHER_MISSING);
            }
            ClaimLock lock = ClaimLock.create(plugin.serverId(), ClaimLock.CLAIM, now());
            inFlight.add(lock.token());
            return plugin.scheduler().supply(() -> storage().lock(mailId, uuid, MailState.PENDING, lock)).thenCompose(won -> {
                if (!won) {
                    inFlight.remove(lock.token());
                    return done(ClaimResult.Status.GONE);
                }
                return handOver(player, mail, lock.token());
            });
        }).exceptionally(failure -> {
            plugin.getLogger().log(Level.WARNING, "Claim of " + mailId + " failed", failure);
            return ClaimResult.of(ClaimResult.Status.ERROR);
        });
    }

    private static CompletableFuture<ClaimResult> done(ClaimResult.Status status) {
        return CompletableFuture.completedFuture(ClaimResult.of(status));
    }

    /** Step two and three of a claim; the mail is locked with {@code token}. */
    private CompletableFuture<ClaimResult> handOver(Player player, MailRecord mail, String token) {
        CompletableFuture<ClaimResult> result = new CompletableFuture<>();
        plugin.scheduler().atPlayer(player, () -> {
            try {
                handOverOnPlayerThread(player, mail, token, result);
            } catch (Throwable failure) {
                // Nothing was handed over unless the receipt was written; recovery decides on the next join.
                plugin.getLogger().log(Level.SEVERE, "Claim of " + mail.id() + " failed on the player's thread", failure);
                inFlight.remove(token);
                result.complete(ClaimResult.of(ClaimResult.Status.ERROR));
            }
        }, () -> {
            // The player left before their thread ran the delivery: undo the lock.
            plugin.scheduler().run(() -> storage().release(mail.id(), token, MailState.PENDING, now()))
                    .whenComplete((ignored, failure) -> inFlight.remove(token));
            result.complete(ClaimResult.of(ClaimResult.Status.OFFLINE));
        });
        return result;
    }

    private void handOverOnPlayerThread(Player player, MailRecord mail, String token, CompletableFuture<ClaimResult> result) {
        List<ItemStack> decoded = new ArrayList<>();
        int unreadable = 0;
        for (String encoded : mail.items()) {
            ItemStack item = ItemSerializer.tryDeserialize(encoded);
            if (item == null) unreadable++;
            decoded.add(item);
        }
        boolean otherRewards = mail.money() > 0 || mail.voucherId() != null || !mail.commands().isEmpty();
        InventorySpace.Plan plan = InventorySpace.plan(player.getInventory().getStorageContents(), decoded);
        List<Integer> delivered = new ArrayList<>(plan.delivered());
        List<ItemStack> dropped = new ArrayList<>();
        if (plugin.settings().dropOverflow) {
            for (int i = 0; i < decoded.size(); i++) {
                if (decoded.get(i) != null && !delivered.contains(i)) {
                    dropped.add(decoded.get(i));
                    delivered.add(i);
                }
            }
        }
        boolean anything = !delivered.isEmpty() || otherRewards || mail.items().isEmpty();
        if (!anything) {
            // Nothing fits (or nothing can be read here): unlock and leave everything in the mailbox.
            int stacks = mail.items().size();
            plugin.scheduler().run(() -> storage().release(mail.id(), token, MailState.PENDING, now()))
                    .whenComplete((ignored, failure) -> inFlight.remove(token));
            result.complete(new ClaimResult(ClaimResult.Status.INVENTORY_FULL, 0, stacks, 0, unreadable, false));
            return;
        }
        // The stacks and the receipt change in the same tick, so any save of the player has both or neither.
        player.getInventory().setStorageContents(plan.contents());
        for (ItemStack item : dropped) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        writeReceipt(player, token, delivered);
        crashTest(player);

        List<String> remaining = new ArrayList<>();
        List<ItemStack> handed = new ArrayList<>();
        for (int i = 0; i < mail.items().size(); i++) {
            if (delivered.contains(i)) {
                if (decoded.get(i) != null) handed.add(decoded.get(i));
            } else {
                remaining.add(mail.items().get(i));
            }
        }
        finish(player.getUniqueId(), player.getName(), mail, token, remaining, handed, dropped.size(), unreadable, result, 0);
    }

    private void finish(UUID uuid, String name, MailRecord mail, String token, List<String> remaining, List<ItemStack> handed,
                        int dropped, int unreadable, CompletableFuture<ClaimResult> result, int attempt) {
        plugin.scheduler().supply(() -> storage().finishClaim(mail.id(), token, remaining, now())).whenComplete((ok, failure) -> {
            if (failure != null) {
                if (attempt < 3) {
                    plugin.scheduler().globalLater(() -> finish(uuid, name, mail, token, remaining, handed, dropped, unreadable,
                            result, attempt + 1), 40L);
                    return;
                }
                // The receipt in the player's data finishes it when they next join.
                plugin.getLogger().log(Level.WARNING, "Could not save the claim of " + mail.id() + "; it is finished on the next join", failure);
                inFlight.remove(token);
                result.complete(ClaimResult.of(ClaimResult.Status.ERROR));
                return;
            }
            inFlight.remove(token);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                plugin.scheduler().atPlayer(online, () -> clearReceipt(online, token));
            }
            if (!Boolean.TRUE.equals(ok)) {
                plugin.audit().log("CLAIM_CONFLICT", "mail", mail.id(), "player", name);
                plugin.getLogger().severe("Claim lock of mail " + mail.id() + " was taken away during delivery (staff removal?).");
                result.complete(ClaimResult.of(ClaimResult.Status.ERROR));
                return;
            }
            giveRewards(uuid, name, mail);
            plugin.audit().log("CLAIM", "mail", mail.id(), "kind", mail.kind(), "player", name, "stacks", handed.size(),
                    "left", remaining.size(), "dropped", dropped, "money", mail.money(), "voucher", mail.voucherId(),
                    "commands", mail.commands().size());
            plugin.fireAsync(new MailClaimEvent(uuid, mail.id(), mail.kind(), handed, mail.money(), !remaining.isEmpty()));
            plugin.summaries().refresh(uuid);
            ClaimResult.Status status = remaining.isEmpty() ? ClaimResult.Status.CLAIMED : ClaimResult.Status.PARTIAL;
            result.complete(new ClaimResult(status, handed.size(), remaining.size(), mail.money(), unreadable,
                    mail.voucherId() != null || !mail.commands().isEmpty()));
        });
    }

    /** Money, voucher and commands of a claimed mail. Runs once, after the claim was saved. */
    private void giveRewards(UUID uuid, String name, MailRecord mail) {
        double money = mail.money();
        VoucherDefinition voucher = mail.voucherId() == null ? null : plugin.vouchers().getVoucher(mail.voucherId());
        List<String> commands = new ArrayList<>(Rewards.voucherCommands(voucher));
        commands.addAll(mail.commands());
        if (money <= 0 && commands.isEmpty()) return;
        plugin.scheduler().global(() -> {
            if (money > 0) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
                if (!plugin.economy().deposit(target, money)) {
                    // Keep the money in the mailbox instead of losing it.
                    plugin.getLogger().warning("Could not pay " + money + " to " + name + "; it was put back in their mailbox.");
                    deliver(serverMail(MailKind.DELIVERY, uuid, mail.senderName()).receiverName(name).money(money)
                            .message(mail.message()).expiresAt(0).build());
                }
            }
            Rewards.dispatch(commands, name, uuid, plugin.getLogger());
        });
    }

    /**
     * Test only: with -Ddonutmail.test.crash-after-delivery=save|nosave the server stops dead right after a
     * delivery, before the claim is saved, to prove that recovery neither loses nor duplicates anything.
     * "save" writes the player's data first (as an autosave would), "nosave" does not.
     */
    private void crashTest(Player player) {
        String mode = System.getProperty("donutmail.test.crash-after-delivery", "");
        if (mode.isEmpty()) return;
        if (mode.equals("save")) player.saveData();
        plugin.getLogger().severe("[crash test] stopping the server right after a delivery (" + mode + ")");
        Runtime.getRuntime().halt(137);
    }

    // ── Receipts in the player's save data ──────────────────────────────────

    private Map<String, List<Integer>> readReceipts(Player player) {
        Map<String, List<Integer>> out = new LinkedHashMap<>();
        String raw = player.getPersistentDataContainer().get(receiptKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return out;
        for (String entry : raw.split("\\|")) {
            int colon = entry.indexOf(':');
            if (colon <= 0) continue;
            List<Integer> indexes = new ArrayList<>();
            for (String part : entry.substring(colon + 1).split(",")) {
                try {
                    if (!part.isBlank()) indexes.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException ignored) {
                    // Skip a damaged entry.
                }
            }
            out.put(entry.substring(0, colon), indexes);
        }
        return out;
    }

    private void saveReceipts(Player player, Map<String, List<Integer>> receipts) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        if (receipts.isEmpty()) {
            data.remove(receiptKey);
            return;
        }
        StringBuilder out = new StringBuilder();
        receipts.forEach((token, indexes) -> {
            if (out.length() > 0) out.append('|');
            out.append(token).append(':');
            for (int i = 0; i < indexes.size(); i++) {
                if (i > 0) out.append(',');
                out.append(indexes.get(i));
            }
        });
        data.set(receiptKey, PersistentDataType.STRING, out.toString());
    }

    private void writeReceipt(Player player, String token, List<Integer> delivered) {
        Map<String, List<Integer>> receipts = readReceipts(player);
        receipts.put(token, delivered);
        saveReceipts(player, receipts);
    }

    private void clearReceipt(Player player, String token) {
        Map<String, List<Integer>> receipts = readReceipts(player);
        if (receipts.remove(token) != null) saveReceipts(player, receipts);
    }

    // ── Recovery of interrupted claims and payments ─────────────────────────

    /** On join: finishes or undoes claims of this player that this server started and did not finish. */
    public void recover(Player player) {
        UUID uuid = player.getUniqueId();
        plugin.scheduler().supply(() -> storage().active(uuid)).thenAccept(active -> {
            List<MailRecord> stale = active.stream()
                    .filter(m -> m.state() == MailState.CLAIMING && plugin.serverId().equals(m.claimServer())
                            && !inFlight.contains(m.claimToken()))
                    .toList();
            plugin.scheduler().atPlayer(player, () -> recoverOnPlayerThread(player, stale));
        }).exceptionally(failure -> {
            plugin.getLogger().log(Level.WARNING, "Could not check interrupted claims of " + player.getName(), failure);
            return null;
        });
    }

    private void recoverOnPlayerThread(Player player, List<MailRecord> stale) {
        Map<String, List<Integer>> receipts = readReceipts(player);
        Set<String> staleTokens = ConcurrentHashMap.newKeySet();
        for (MailRecord mail : stale) {
            String token = mail.claimToken();
            staleTokens.add(token);
            if (ClaimLock.PAY.equals(mail.claimAction())) {
                releasePayment(mail);
                continue;
            }
            List<Integer> delivered = receipts.get(token);
            if (delivered == null) {
                plugin.scheduler().run(() -> {
                    if (storage().release(mail.id(), token, MailState.PENDING, now())) {
                        plugin.audit().log("CLAIM_UNDONE", "mail", mail.id(), "player", player.getName(), "why", "interrupted before delivery");
                    }
                });
                continue;
            }
            List<String> remaining = new ArrayList<>();
            for (int i = 0; i < mail.items().size(); i++) {
                if (!delivered.contains(i)) remaining.add(mail.items().get(i));
            }
            inFlight.add(token);
            UUID uuid = player.getUniqueId();
            String name = player.getName();
            plugin.scheduler().supply(() -> storage().finishClaim(mail.id(), token, remaining, now())).whenComplete((ok, failure) -> {
                inFlight.remove(token);
                if (failure != null || !Boolean.TRUE.equals(ok)) return;
                plugin.scheduler().atPlayer(player, () -> {
                    clearReceipt(player, token);
                    plugin.messages().send(player, "mailbox.recovered");
                });
                giveRewards(uuid, name, mail);
                plugin.audit().log("CLAIM_RECOVERED", "mail", mail.id(), "player", name, "stacks", delivered.size(), "left", remaining.size());
                plugin.summaries().refresh(uuid);
            });
        }
        // Receipts of claims that were finished normally but whose cleanup did not run.
        boolean changed = receipts.keySet().removeIf(token -> !staleTokens.contains(token) && !inFlight.contains(token));
        if (changed) saveReceipts(player, receipts);
    }

    private void releasePayment(MailRecord mail) {
        plugin.scheduler().run(() -> {
            if (storage().release(mail.id(), mail.claimToken(), MailState.COD, now())) {
                plugin.audit().log("PAY_INTERRUPTED", "mail", mail.id(), "buyer", mail.receiver(), "price", mail.codPrice(),
                        "note", "the server stopped during payment; the parcel waits for payment again; check the buyer's balance");
                plugin.getLogger().warning("A cash-on-delivery payment for mail " + mail.id() + " was interrupted by a restart. "
                        + "The parcel waits for payment again; see the audit log.");
            }
        });
    }

    /** Startup and periodic: interrupted payments go back to waiting; interrupted claims of online players are resolved. */
    public void recoverServerLocks(long olderThanMillis) {
        long cutoff = now() - olderThanMillis;
        for (MailRecord mail : storage().claiming(plugin.serverId())) {
            if (inFlight.contains(mail.claimToken()) || mail.claimAt() > cutoff) continue;
            if (ClaimLock.PAY.equals(mail.claimAction())) {
                releasePayment(mail);
            } else {
                Player online = Bukkit.getPlayer(mail.receiver());
                if (online != null) recover(online);
            }
        }
    }

    // ── Cash on delivery ────────────────────────────────────────────────────

    public CompletableFuture<PayResult> pay(Player player, UUID mailId) {
        UUID uuid = player.getUniqueId();
        if (!plugin.economy().available()) {
            return CompletableFuture.completedFuture(PayResult.of(PayResult.Status.NO_ECONOMY));
        }
        return plugin.scheduler().supply(() -> storage().mail(mailId).orElse(null)).thenCompose(mail -> {
            if (mail == null || !mail.receiver().equals(uuid) || mail.state() != MailState.COD || !mail.awaitingPayment()
                    || mail.isExpired(now())) {
                return CompletableFuture.completedFuture(PayResult.of(PayResult.Status.NOT_WAITING));
            }
            double price = mail.codPrice();
            ClaimLock lock = ClaimLock.create(plugin.serverId(), ClaimLock.PAY, now());
            inFlight.add(lock.token());
            return plugin.scheduler().supply(() -> storage().lock(mailId, uuid, MailState.COD, lock)).thenCompose(won -> {
                if (!won) {
                    inFlight.remove(lock.token());
                    return CompletableFuture.completedFuture(PayResult.of(PayResult.Status.NOT_WAITING));
                }
                plugin.audit().log("PAY_START", "mail", mailId, "buyer", player.getName(), "price", price);
                return plugin.scheduler().onGlobal(() -> plugin.economy().withdraw(player, price)).thenCompose(taken -> {
                    if (!taken) {
                        return plugin.scheduler().supply(() -> storage().release(mailId, lock.token(), MailState.COD, now()))
                                .handle((ignored, failure) -> {
                                    inFlight.remove(lock.token());
                                    return new PayResult(PayResult.Status.NOT_ENOUGH, price, null);
                                });
                    }
                    return settlePayment(player, mail, lock.token(), price);
                });
            });
        }).exceptionally(failure -> {
            plugin.getLogger().log(Level.WARNING, "Payment for " + mailId + " failed", failure);
            return PayResult.of(PayResult.Status.ERROR);
        });
    }

    private CompletableFuture<PayResult> settlePayment(Player player, MailRecord mail, String token, double price) {
        double tax = Math.round(price * plugin.settings().codTaxPercent) / 100.0;
        double toSeller = Math.max(0, price - tax);
        String message = plugin.messages().format("mail.receipt", "player", player.getName(), "price", plugin.economy().format(price))
                + (tax > 0 ? "\n" + plugin.messages().format("mail.receipt-tax", "tax", plugin.economy().format(tax)) : "");
        long now = now();
        MailRecord receipt = MailRecord.builder().kind(MailKind.RECEIPT).receiver(mail.senderId()).receiverName(mail.senderName())
                .senderId(player.getUniqueId()).senderName(player.getName()).message(message).money(toSeller).refId(mail.id())
                .createdAt(now).updatedAt(now).expiresAt(0).state(MailState.PENDING).build();
        return plugin.scheduler().supply(() -> storage().completePayment(mail.id(), token, receipt, now)).handle((ok, failure) -> {
            if (failure == null && Boolean.TRUE.equals(ok)) return true;
            // Not saved: give the money back and let the parcel wait again.
            plugin.getLogger().log(Level.WARNING, "Could not save the payment of " + mail.id() + "; refunding", failure);
            plugin.scheduler().global(() -> plugin.economy().deposit(player, price));
            plugin.scheduler().run(() -> storage().release(mail.id(), token, MailState.COD, now()));
            plugin.audit().log("PAY_REFUNDED", "mail", mail.id(), "buyer", player.getName(), "price", price);
            return false;
        }).thenCompose(saved -> {
            inFlight.remove(token);
            if (!saved) {
                return CompletableFuture.completedFuture(new PayResult(PayResult.Status.FAILED, price, null));
            }
            plugin.audit().log("PAID", "mail", mail.id(), "buyer", player.getName(), "seller", mail.senderName(), "price", price,
                    "tax", tax, "receipt", receipt.id());
            plugin.fireAsync(new ParcelPaidEvent(mail.id(), player.getUniqueId(), mail.senderId(), price, toSeller));
            plugin.notifier().parcelPaid(receipt, price);
            return claim(player, mail.id()).thenApply(claim -> new PayResult(PayResult.Status.PAID, price, claim));
        });
    }

    /** The recipient refuses a cash-on-delivery parcel: it goes back to the sender. */
    public CompletableFuture<Boolean> decline(Player player, UUID mailId) {
        UUID uuid = player.getUniqueId();
        return plugin.scheduler().supply(() -> {
            MailRecord mail = storage().mail(mailId).orElse(null);
            if (mail == null || !uuid.equals(mail.receiver()) || mail.state() != MailState.COD || !mail.fromPlayer()) return null;
            MailRecord back = returnMail(mail, "returned-declined", mail.receiverName() == null ? player.getName() : mail.receiverName());
            boolean ok = storage().returnToSender(mailId, EnumSet.of(MailState.COD), "DECLINED", back, uuid, null, false, now());
            return ok ? new MailRecord[]{mail, back} : null;
        }).thenApply(pair -> {
            if (pair == null) return false;
            returned(pair[0], pair[1], ParcelReturnedEvent.Reason.DECLINED, player.getName());
            return true;
        });
    }

    /** The sender takes back a parcel nobody claimed or paid yet. */
    public CompletableFuture<Boolean> recall(Player player, UUID mailId) {
        UUID uuid = player.getUniqueId();
        return plugin.scheduler().supply(() -> {
            MailRecord mail = storage().mail(mailId).orElse(null);
            if (mail == null || !uuid.equals(mail.senderId()) || mail.kind() != MailKind.PARCEL || !RETURNABLE.contains(mail.state())
                    || mail.codPaidAt() != 0) {
                return null;
            }
            MailRecord back = returnMail(mail, "returned-recalled", mail.receiverName());
            boolean ok = storage().returnToSender(mailId, RETURNABLE, "RECALLED", back, null, uuid, false, now());
            return ok ? new MailRecord[]{mail, back} : null;
        }).thenApply(pair -> {
            if (pair == null) return false;
            returned(pair[0], pair[1], ParcelReturnedEvent.Reason.RECALLED, player.getName());
            return true;
        });
    }

    /** The parcel's items as a new mail to its sender. */
    MailRecord returnMail(MailRecord mail, String messageKey, String otherPlayer) {
        long now = now();
        String name = otherPlayer == null ? "?" : otherPlayer;
        return MailRecord.builder().kind(MailKind.RETURN).receiver(mail.senderId()).receiverName(mail.senderName())
                .senderId(mail.receiver()).senderName(name).message(plugin.messages().format("mail." + messageKey, "player", name))
                .items(mail.items()).refId(mail.id()).createdAt(now).updatedAt(now).expiresAt(0).state(MailState.PENDING).build();
    }

    private void returned(MailRecord original, MailRecord back, ParcelReturnedEvent.Reason reason, String actor) {
        plugin.audit().log("RETURN", "mail", original.id(), "reason", reason, "by", actor, "from", original.receiverName(),
                "to", original.senderName(), "stacks", original.items().size(), "return", back.id());
        plugin.fireAsync(new ParcelReturnedEvent(original.id(), back.id(), original.senderId(), original.receiver(), reason));
        plugin.notifier().parcelReturned(back, reason);
        plugin.summaries().refresh(original.receiver());
    }

    // ── Expiry ──────────────────────────────────────────────────────────────

    /** Expired parcels go back to their senders; other expired mail is closed. Safe to run on every server at once. */
    public void sweepExpired() {
        long now = now();
        for (MailRecord mail : storage().due(now, 200)) {
            try {
                if (mail.kind() == MailKind.PARCEL && mail.fromPlayer() && mail.codPaidAt() == 0 && plugin.settings().returnToSender
                        && !mail.items().isEmpty()) {
                    MailRecord back = returnMail(mail, "returned-expired", mail.receiverName());
                    if (storage().returnToSender(mail.id(), RETURNABLE, "EXPIRED", back, null, null, true, now)) {
                        returned(mail, back, ParcelReturnedEvent.Reason.EXPIRED, "expiry");
                    }
                } else if (storage().expire(mail.id(), now)) {
                    plugin.audit().log("EXPIRE", "mail", mail.id(), "kind", mail.kind(), "to", mail.receiver(), "from", mail.senderName(),
                            "stacks", mail.items().size(), "money", mail.money());
                    plugin.summaries().refresh(mail.receiver());
                }
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "Could not expire mail " + mail.id(), failure);
            }
        }
    }

    // ── Staff ───────────────────────────────────────────────────────────────

    /** Player parcels go back to the sender; other mail is deleted. Returns a message key, or null when it changed meanwhile. */
    public CompletableFuture<String> adminRemove(CommandSender staff, UUID mailId) {
        return plugin.scheduler().supply(() -> {
            MailRecord mail = storage().mail(mailId).orElse(null);
            if (mail == null || !RETURNABLE.contains(mail.state())) return null;
            if (mail.kind() == MailKind.PARCEL && mail.fromPlayer() && mail.codPaidAt() == 0 && !mail.items().isEmpty()) {
                MailRecord back = returnMail(mail, "returned-removed", mail.receiverName());
                if (storage().returnToSender(mailId, RETURNABLE, "REMOVED", back, null, null, false, now())) {
                    returned(mail, back, ParcelReturnedEvent.Reason.REMOVED, staff.getName());
                    return "admin.removed-returned";
                }
                return null;
            }
            if (storage().remove(mailId, RETURNABLE, "REMOVED", now())) {
                plugin.audit().log("REMOVE", "mail", mailId, "staff", staff.getName(), "kind", mail.kind(), "to", mail.receiver(),
                        "stacks", mail.items().size(), "money", mail.money());
                plugin.summaries().refresh(mail.receiver());
                return "";
            }
            return null;
        });
    }

    // ── Reading ─────────────────────────────────────────────────────────────

    public void markRead(MailRecord mail) {
        if (mail.readAt() != 0) return;
        plugin.scheduler().run(() -> {
            if (storage().markRead(mail.id(), now())) plugin.summaries().refresh(mail.receiver());
        });
    }

    /** Text of a mail for menus: player notes are shown as typed (escaped), server text keeps its formatting. */
    public static String displayMessage(MailRecord mail) {
        if (mail.kind() == MailKind.PARCEL) {
            return Text.escape(mail.message());
        }
        return mail.message();
    }

    // ── Busy guard ──────────────────────────────────────────────────────────

    /** Runs an action unless one is already running for the player. Returns false when busy. */
    public <T> boolean guarded(UUID player, java.util.function.Supplier<CompletableFuture<T>> action,
                               java.util.function.Consumer<T> then) {
        if (!busy.add(player)) return false;
        CompletableFuture<T> future;
        try {
            future = action.get();
        } catch (RuntimeException failure) {
            busy.remove(player);
            throw failure;
        }
        future.whenComplete((value, failure) -> {
            busy.remove(player);
            if (failure != null) {
                plugin.getLogger().log(Level.WARNING, "Mailbox action failed", failure);
            }
            then.accept(value);
        });
        return true;
    }
}
