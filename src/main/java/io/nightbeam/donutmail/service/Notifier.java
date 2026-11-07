package io.nightbeam.donutmail.service;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.api.event.ParcelReturnedEvent;
import io.nightbeam.donutmail.config.Messages;
import io.nightbeam.donutmail.config.Settings;
import io.nightbeam.donutmail.model.Campaign;
import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.MailState;
import io.nightbeam.donutmail.util.Sounds;
import io.nightbeam.donutmail.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Locale;

/** Chat, titles, the action bar and sounds: new mail, payments, returns, campaigns, daily rewards and the join summary. */
public final class Notifier {
    private final DonutMailPlugin plugin;

    public Notifier(DonutMailPlugin plugin) {
        this.plugin = plugin;
    }

    private Settings settings() {
        return plugin.settings();
    }

    private Messages messages() {
        return plugin.messages();
    }

    /** A mail was saved on this server. Tells the receiver if they are online here; other servers learn through polling. */
    public void mailArrived(MailRecord mail) {
        Player player = Bukkit.getPlayer(mail.receiver());
        if (player == null) return;
        plugin.summaries().refresh(mail.receiver());
        if (mail.kind() == MailKind.CAMPAIGN || mail.kind() == MailKind.DAILY || mail.kind() == MailKind.RECEIPT
                || mail.kind() == MailKind.RETURN || mail.kind() == MailKind.CODE) {
            return;
        }
        live(player, mail);
    }

    /** A mail saved by another server (MySQL polling). Called on a storage thread. */
    public void remoteMail(MailRecord mail) {
        Player player = Bukkit.getPlayer(mail.receiver());
        if (player == null) return;
        plugin.summaries().refresh(mail.receiver());
        switch (mail.kind()) {
            case RECEIPT -> {
                double price = mail.refId() == null ? mail.money()
                        : plugin.storage().mail(mail.refId()).map(MailRecord::codPrice).orElse(mail.money());
                parcelPaid(mail, price);
            }
            case RETURN -> {
                ParcelReturnedEvent.Reason reason = ParcelReturnedEvent.Reason.EXPIRED;
                if (mail.refId() != null) {
                    String stored = plugin.storage().mail(mail.refId()).map(MailRecord::reason).orElse(null);
                    try {
                        if (stored != null) reason = ParcelReturnedEvent.Reason.valueOf(stored);
                    } catch (IllegalArgumentException ignored) {
                        // Keep the default.
                    }
                }
                parcelReturned(mail, reason);
            }
            case CAMPAIGN, CODE, DAILY -> {
                // The player did this themselves.
            }
            default -> live(player, mail);
        }
    }

    private void live(Player player, MailRecord mail) {
        if (!settings().liveNotices) return;
        plugin.scheduler().atPlayer(player, () -> {
            String sender = mail.senderName();
            if (settings().notifyChat) {
                if (mail.kind() == MailKind.PARCEL && mail.state() == MailState.COD) {
                    messages().send(player, "notify.new-cod", "sender", sender, "price", plugin.economy().format(mail.codPrice()));
                } else if (mail.kind() == MailKind.PARCEL) {
                    messages().send(player, "notify.new-parcel", "sender", sender);
                } else if (mail.kind().tab() == MailKind.Tab.REWARDS) {
                    messages().send(player, "notify.new-reward", "sender", sender);
                } else {
                    messages().send(player, "notify.new-mail", "sender", sender);
                }
            }
            if (settings().notifyTitle) {
                player.showTitle(Title.title(messages().component("notify.title", "sender", sender),
                        messages().component("notify.subtitle", "sender", sender),
                        Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2500), Duration.ofMillis(500))));
            }
            if (settings().notifyActionBar) {
                player.sendActionBar(messages().component("notify.action-bar", "sender", sender));
            }
            sound(player);
        });
    }

    private void sound(Player player) {
        if (settings().notifySound) {
            Sounds.playSpec(player, settings().notifySoundKey);
        }
    }

    /** The sender of a cash-on-delivery parcel was paid (the receipt is in their mailbox). */
    public void parcelPaid(MailRecord receipt, double price) {
        Player seller = Bukkit.getPlayer(receipt.receiver());
        if (seller == null) return;
        plugin.summaries().refresh(receipt.receiver());
        plugin.scheduler().atPlayer(seller, () -> {
            messages().send(seller, "notify.paid", "player", receipt.senderName(), "price", plugin.economy().format(price));
            Sounds.play(seller, settings().raw, "paid");
        });
    }

    public void parcelReturned(MailRecord back, ParcelReturnedEvent.Reason reason) {
        Player sender = Bukkit.getPlayer(back.receiver());
        if (sender == null) return;
        plugin.summaries().refresh(back.receiver());
        if (reason == ParcelReturnedEvent.Reason.RECALLED) return;
        plugin.scheduler().atPlayer(sender, () -> {
            messages().send(sender, "notify.returned", "player", back.senderName(),
                    "reason", Text.trusted(messages().raw("reason." + reason.name().toLowerCase(Locale.ROOT))));
            sound(sender);
        });
    }

    public void campaignStarted(Campaign campaign) {
        if (!settings().announceCampaigns) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.campaigns().canSee(player, campaign)) continue;
            plugin.summaries().refresh(player.getUniqueId());
            plugin.scheduler().atPlayer(player, () -> {
                messages().sendPlain(player, "notify.campaign", "sender", campaign.senderName());
                sound(player);
            });
        }
    }

    public void dailyDelivered(Player player, int day, int streak) {
        plugin.scheduler().atPlayer(player, () -> messages().send(player, "notify.daily", "day", String.valueOf(day),
                "streak", String.valueOf(streak)));
    }

    /** "Welcome back" with counts and a clickable link, a moment after joining. */
    public void joinSummary(Player player) {
        if (!settings().joinSummary) return;
        plugin.scheduler().atPlayerLater(player, () -> plugin.summaries().refresh(player.getUniqueId()).thenAccept(summary -> {
            if (summary.waiting() == 0) return;
            plugin.scheduler().atPlayer(player, () -> {
                messages().sendPlain(player, "notify.summary-header", "player", player.getName());
                int unreadInbox = Math.min(summary.unread(), summary.inbox());
                if (unreadInbox > 0) messages().sendPlain(player, "notify.summary-unread", "count", String.valueOf(unreadInbox));
                int plainParcels = summary.parcels() - summary.cod();
                if (plainParcels > 0) messages().sendPlain(player, "notify.summary-parcels", "count", String.valueOf(plainParcels));
                if (summary.cod() > 0) messages().sendPlain(player, "notify.summary-cod", "count", String.valueOf(summary.cod()));
                if (summary.rewards() > 0) messages().sendPlain(player, "notify.summary-rewards", "count", String.valueOf(summary.rewards()));
                if (summary.campaigns() > 0) messages().sendPlain(player, "notify.summary-campaigns", "count", String.valueOf(summary.campaigns()));
                messages().sendPlain(player, "notify.summary-open");
                sound(player);
            });
        }), Math.max(1, settings().joinDelaySeconds) * 20L);
    }

    public Component line(String key, String... keyValues) {
        return messages().component(key, keyValues);
    }
}
