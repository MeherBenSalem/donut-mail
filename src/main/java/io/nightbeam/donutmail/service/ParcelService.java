package io.nightbeam.donutmail.service;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.api.event.MailSendEvent;
import io.nightbeam.donutmail.config.Settings;
import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.MailState;
import io.nightbeam.donutmail.model.PlayerRecord;
import io.nightbeam.donutmail.util.ItemNames;
import io.nightbeam.donutmail.util.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Player parcels: who may send what to whom, postage, and sending. */
public final class ParcelService {
    private final DonutMailPlugin plugin;
    private final Map<UUID, Long> lastSent = new ConcurrentHashMap<>();

    public ParcelService(DonutMailPlugin plugin) {
        this.plugin = plugin;
    }

    public record Recipient(UUID id, String name) {
    }

    /** Finds a player by name: online, known to DonutMail, or known to this server. Empty when never seen. */
    public CompletableFuture<Optional<Recipient>> resolve(String name) {
        if (name == null || name.isBlank() || name.length() > 36) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(new Recipient(online.getUniqueId(), online.getName())));
        }
        return plugin.scheduler().supply(() -> {
            Optional<PlayerRecord> known = plugin.storage().playerByName(name);
            if (known.isPresent()) {
                PlayerRecord record = known.get();
                return Optional.of(new Recipient(record.id(), record.name() == null ? name : record.name()));
            }
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
            if (cached != null && (cached.hasPlayedBefore() || cached.isOnline())) {
                return Optional.of(new Recipient(cached.getUniqueId(), cached.getName() == null ? name : cached.getName()));
            }
            return Optional.empty();
        });
    }

    public double postage(int stacks, Player sender) {
        Settings settings = plugin.settings();
        if (!settings.postageEnabled || (sender != null && sender.hasPermission("donutmail.postage.exempt"))) return 0;
        return Math.max(0, settings.postageFlat + settings.postagePerStack * stacks);
    }

    /** Checks that do not need the items, so the compose menu can say no before taking anything. */
    public CompletableFuture<SendResult> check(Player sender, Recipient to, double cod) {
        Settings settings = plugin.settings();
        UUID from = sender.getUniqueId();
        boolean bypass = sender.hasPermission("donutmail.bypass.limits");
        if (!settings.parcelsEnabled) return done(SendResult.of(SendResult.Status.DISABLED));
        if (to.id().equals(from) && !settings.allowSelf) return done(SendResult.of(SendResult.Status.SELF));
        if (cod > 0) {
            if (!settings.codEnabled) return done(SendResult.of(SendResult.Status.COD_DISABLED));
            if (!plugin.economy().available()) return done(SendResult.of(SendResult.Status.NO_ECONOMY));
            if (cod < settings.codMin || cod > settings.codMax) return done(SendResult.of(SendResult.Status.COD_PRICE));
        }
        if (!bypass && settings.cooldownSeconds > 0) {
            long last = lastSent.getOrDefault(from, 0L);
            long wait = last + settings.cooldownSeconds * 1000L - System.currentTimeMillis();
            if (wait > 0) return done(SendResult.of(SendResult.Status.COOLDOWN, String.valueOf((wait + 999) / 1000)));
        }
        long dayStart = LocalDate.now(plugin.daily().zone).atStartOfDay(plugin.daily().zone).toInstant().toEpochMilli();
        return plugin.scheduler().supply(() -> {
            Optional<PlayerRecord> record = plugin.storage().player(to.id());
            if (record.isPresent() && !record.get().acceptParcels() && !to.id().equals(from)) {
                return SendResult.of(SendResult.Status.REFUSING);
            }
            if (plugin.storage().isBlocked(to.id(), from)) {
                return SendResult.of(SendResult.Status.BLOCKED);
            }
            if (!bypass) {
                if (settings.maxPerDay > 0 && plugin.storage().countSentSince(from, dayStart) >= settings.maxPerDay) {
                    return SendResult.of(SendResult.Status.DAILY_LIMIT, String.valueOf(settings.maxPerDay));
                }
                if (settings.maxPendingPerRecipient > 0 && plugin.storage().countActiveFrom(from, to.id()) >= settings.maxPendingPerRecipient) {
                    return SendResult.of(SendResult.Status.RECIPIENT_LIMIT, String.valueOf(settings.maxPendingPerRecipient));
                }
                if (settings.mailboxCapacity > 0
                        && plugin.storage().countActive(to.id(), EnumSet.of(MailKind.PARCEL)) >= settings.mailboxCapacity) {
                    return SendResult.of(SendResult.Status.MAILBOX_FULL);
                }
            }
            return SendResult.of(SendResult.Status.SENT);
        });
    }

    /** The first blacklisted item, or null. */
    public ItemStack blacklisted(List<ItemStack> items) {
        for (ItemStack item : items) {
            if (item != null && plugin.settings().blacklist.contains(item.getType())) return item;
        }
        return null;
    }

    /**
     * Sends a parcel. The items are already out of the sender's hands; when the result is not SENT the
     * caller gives them back.
     */
    public CompletableFuture<SendResult> send(Player sender, Recipient to, List<ItemStack> items, String note, double cod) {
        if (items.isEmpty()) return done(SendResult.of(SendResult.Status.EMPTY));
        ItemStack banned = blacklisted(items);
        if (banned != null) return done(SendResult.of(SendResult.Status.BLACKLISTED, ItemNames.plain(banned)));
        double fee = postage(items.size(), sender);
        return check(sender, to, cod).thenCompose(check -> {
            if (!check.sent()) return done(check);
            if (fee > 0 && !plugin.economy().available()) return done(SendResult.of(SendResult.Status.NO_ECONOMY));
            CompletableFuture<Boolean> charged = fee > 0
                    ? plugin.scheduler().onGlobal(() -> plugin.economy().withdraw(sender, fee))
                    : CompletableFuture.completedFuture(true);
            return charged.thenCompose(paid -> {
                if (!paid) return done(SendResult.of(SendResult.Status.POSTAGE, plugin.economy().format(fee)));
                return fireSend(sender, to, items, note, cod).thenCompose(event -> {
                    if (event.isCancelled()) {
                        refund(sender, fee);
                        return done(SendResult.of(SendResult.Status.CANCELLED, event.getCancelMessage() == null ? "" : event.getCancelMessage()));
                    }
                    MailRecord mail = parcel(sender.getUniqueId(), sender.getName(), to, items, note, cod);
                    return plugin.scheduler().supply(() -> {
                        plugin.storage().insert(mail);
                        return mail;
                    }).handle((saved, failure) -> {
                        if (failure != null) {
                            refund(sender, fee);
                            plugin.getLogger().warning("Could not save a parcel from " + sender.getName() + ": " + failure.getMessage());
                            return SendResult.of(SendResult.Status.ERROR);
                        }
                        lastSent.put(sender.getUniqueId(), System.currentTimeMillis());
                        sent(saved, fee);
                        return new SendResult(SendResult.Status.SENT, saved.id(), fee > 0 ? plugin.economy().format(fee) : "");
                    });
                });
            });
        });
    }

    /** A parcel from another plugin on behalf of a player: blocks and the refuse toggle apply, limits do not. */
    public CompletableFuture<SendResult> sendFromApi(UUID from, String fromName, UUID to, String toName, List<ItemStack> items,
                                                     String note, double cod) {
        if (items == null || items.isEmpty()) return done(SendResult.of(SendResult.Status.EMPTY));
        if (cod > 0 && !plugin.economy().available()) return done(SendResult.of(SendResult.Status.NO_ECONOMY));
        Recipient recipient = new Recipient(to, toName);
        return plugin.scheduler().supply(() -> {
            Optional<PlayerRecord> record = plugin.storage().player(to);
            if (record.isPresent() && !record.get().acceptParcels()) return SendResult.of(SendResult.Status.REFUSING);
            if (plugin.storage().isBlocked(to, from)) return SendResult.of(SendResult.Status.BLOCKED);
            MailSendEvent event = new MailSendEvent(MailKind.PARCEL, to, from, fromName, items, 0, cod, note);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return SendResult.of(SendResult.Status.CANCELLED, event.getCancelMessage() == null ? "" : event.getCancelMessage());
            MailRecord mail = parcel(from, fromName, recipient, items, note, cod);
            plugin.storage().insert(mail);
            sent(mail, 0);
            return new SendResult(SendResult.Status.SENT, mail.id(), "");
        });
    }

    private MailRecord parcel(UUID from, String fromName, Recipient to, List<ItemStack> items, String note, double cod) {
        long now = System.currentTimeMillis();
        String text = note == null ? "" : note.strip();
        int max = plugin.settings().noteMaxLength;
        if (max > 0 && text.length() > max) text = text.substring(0, max);
        return MailRecord.builder().kind(MailKind.PARCEL).receiver(to.id()).receiverName(to.name()).senderId(from).senderName(fromName)
                .message(text).items(ItemSerializer.serializeAll(items)).codPrice(cod > 0 ? cod : 0)
                .state(cod > 0 ? MailState.COD : MailState.PENDING).createdAt(now).updatedAt(now)
                .expiresAt(plugin.settings().parcelExpiry(now)).build();
    }

    private void sent(MailRecord mail, double fee) {
        plugin.audit().log("SEND", "mail", mail.id(), "from", mail.senderName(), "fromId", mail.senderId(), "to", mail.receiverName(),
                "toId", mail.receiver(), "stacks", mail.items().size(), "cod", mail.codPrice(), "postage", fee,
                "note", mail.message());
        plugin.notifier().mailArrived(mail);
    }

    private CompletableFuture<MailSendEvent> fireSend(Player sender, Recipient to, List<ItemStack> items, String note, double cod) {
        CompletableFuture<MailSendEvent> future = new CompletableFuture<>();
        Runnable fire = () -> {
            MailSendEvent event = new MailSendEvent(MailKind.PARCEL, to.id(), sender.getUniqueId(), sender.getName(), items, 0, cod, note);
            try {
                Bukkit.getPluginManager().callEvent(event);
            } catch (RuntimeException failure) {
                plugin.getLogger().warning("A MailSendEvent listener failed: " + failure.getMessage());
            }
            future.complete(event);
        };
        plugin.scheduler().atPlayer(sender, fire, () -> {
            MailSendEvent event = new MailSendEvent(MailKind.PARCEL, to.id(), sender.getUniqueId(), sender.getName(), items, 0, cod, note);
            event.setCancelled(true);
            future.complete(event);
        });
        return future;
    }

    private void refund(Player sender, double fee) {
        if (fee > 0) plugin.scheduler().global(() -> plugin.economy().deposit(sender, fee));
    }

    private static CompletableFuture<SendResult> done(SendResult result) {
        return CompletableFuture.completedFuture(result);
    }
}
