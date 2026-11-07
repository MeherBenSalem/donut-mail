package io.nightbeam.donutmail.gui;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.config.GuiConfig;
import io.nightbeam.donutmail.model.Campaign;
import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.MailState;
import io.nightbeam.donutmail.model.VoucherDefinition;
import io.nightbeam.donutmail.service.MailService;
import io.nightbeam.donutmail.util.Durations;
import io.nightbeam.donutmail.util.ItemBuilder;
import io.nightbeam.donutmail.util.ItemNames;
import io.nightbeam.donutmail.util.ItemSerializer;
import io.nightbeam.donutmail.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** How mail and campaigns look in the lists (gui.yml: entries and lore). */
public final class MailIcons {
    private static final int MAX_ITEM_LINES = 5;
    private static final int MAX_MESSAGE_LINES = 6;

    /** Whose view: the receiver's mailbox, the sender's Sent tab, or staff. */
    public enum View { RECEIVER, SENT, ADMIN }

    private MailIcons() {
    }

    public static String entryKey(MailRecord mail, View view) {
        if (view == View.SENT) return "sent";
        return switch (mail.kind()) {
            case MESSAGE -> "message";
            case DELIVERY -> mail.hasRewards() ? "delivery" : "message";
            case PARCEL -> mail.state() == MailState.COD || mail.awaitingPayment() ? "cod" : mail.codPaidAt() > 0 ? "paid" : "parcel";
            case RECEIPT -> "receipt";
            case RETURN -> "return";
            case CAMPAIGN -> "campaign";
            case DAILY -> "daily";
            case CODE -> "code";
            case VOTE -> "vote";
            case REWARD -> "reward";
        };
    }

    public static ItemStack mail(DonutMailPlugin plugin, MailRecord mail, View view) {
        GuiConfig gui = plugin.gui();
        String key = "entries." + entryKey(mail, view);
        Map<String, String> values = Text.toMap(
                "sender", mail.senderName(),
                "player", view == View.SENT ? nameOr(mail.receiverName()) : mail.senderName(),
                "price", plugin.economy().format(mail.codPrice()));
        ItemBuilder builder = ItemBuilder.of(gui.material(key, Material.PAPER))
                .name(Text.parse(Text.substitute(gui.text(key + ".name"), values)))
                .model(gui.yaml().getInt(key + ".model", 0));
        List<Component> lore = new ArrayList<>();
        lore.add(gui.line("lore.separator"));
        if (view == View.SENT) {
            lore.add(gui.line("lore.to", "player", nameOr(mail.receiverName())));
        } else {
            lore.add(gui.line("lore.from", "sender", mail.senderName()));
            if (view == View.ADMIN && mail.receiverName() != null) lore.add(gui.line("lore.to", "player", mail.receiverName()));
        }
        message(gui, lore, MailService.displayMessage(mail));
        contents(plugin, lore, mail.items(), mail.money(), mail.voucherId(), mail.commands().size());
        if (mail.codPrice() > 0) {
            lore.add(gui.line(mail.codPaidAt() > 0 ? "lore.status-paid" : "lore.cod", "price", plugin.economy().format(mail.codPrice())));
        }
        lore.add(Component.empty());
        long now = System.currentTimeMillis();
        lore.add(gui.line(view == View.SENT ? "lore.sent-at" : "lore.received", "time", Durations.format(now - mail.createdAt())));
        if (mail.expiresAt() > 0 && mail.state().isActive()) {
            lore.add(gui.line("lore.expires", "time", Durations.format(mail.expiresAt() - now)));
        }
        if (view != View.RECEIVER) {
            Component status = status(plugin, mail);
            if (status != null) lore.add(status);
        }
        if (mail.state() == MailState.CLAIMING) {
            lore.add(gui.line("lore.locked"));
        } else if (view == View.RECEIVER && mail.isUnread()) {
            lore.add(gui.line("lore.new"));
        }
        lore.add(Component.empty());
        switch (view) {
            case RECEIVER -> {
                if (mail.state() == MailState.COD) {
                    lore.add(gui.line("lore.click-cod"));
                } else if (mail.state() == MailState.PENDING) {
                    lore.add(gui.line(mail.hasRewards() ? "lore.click-claim" : "lore.click-read"));
                    if (mail.hasRewards()) lore.add(gui.line("lore.click-preview"));
                }
            }
            case SENT -> lore.add(gui.line("lore.click-view"));
            case ADMIN -> {
                lore.add(gui.line("lore.click-preview"));
                if (mail.state() == MailState.PENDING || mail.state() == MailState.COD) lore.add(gui.line("lore.click-remove"));
            }
        }
        builder.lore(lore).hideAll();
        builder.glow(view == View.RECEIVER && mail.isUnread());
        return builder.build();
    }

    public static ItemStack campaign(DonutMailPlugin plugin, Campaign campaign) {
        GuiConfig gui = plugin.gui();
        String key = "entries.campaign";
        ItemBuilder builder = ItemBuilder.of(gui.material(key, Material.NETHER_STAR))
                .name(Text.parse(Text.substitute(gui.text(key + ".name"), Text.toMap("sender", campaign.senderName(), "player", campaign.senderName()))))
                .model(gui.yaml().getInt(key + ".model", 0));
        List<Component> lore = new ArrayList<>();
        lore.add(gui.line("lore.separator"));
        lore.add(gui.line("lore.from", "sender", campaign.senderName()));
        message(gui, lore, campaign.message());
        contents(plugin, lore, campaign.items(), campaign.money(), campaign.voucherId(), campaign.commands().size());
        lore.add(Component.empty());
        if (campaign.endsAt() > 0) {
            lore.add(gui.line("lore.campaign-ends", "time", Durations.format(campaign.endsAt() - System.currentTimeMillis())));
        }
        lore.add(gui.line("lore.new"));
        lore.add(Component.empty());
        lore.add(gui.line(campaign.hasRewards() ? "lore.click-claim" : "lore.click-read"));
        if (campaign.hasRewards()) lore.add(gui.line("lore.click-preview"));
        return builder.lore(lore).hideAll().glow(true).build();
    }

    private static String nameOr(String name) {
        return name == null ? "?" : name;
    }

    static void message(GuiConfig gui, List<Component> lore, String message) {
        if (message == null || message.isBlank()) return;
        List<String> lines = Text.wrap(message, 34);
        int shown = 0;
        for (String line : lines) {
            if (shown++ >= MAX_MESSAGE_LINES) break;
            lore.add(Text.parse(gui.text("lore.message-line").replace("%line%", line)));
        }
    }

    static void contents(DonutMailPlugin plugin, List<Component> lore, List<String> items, double money, String voucherId,
                         int commands) {
        GuiConfig gui = plugin.gui();
        if (!items.isEmpty() || money > 0 || voucherId != null || commands > 0) lore.add(Component.empty());
        if (!items.isEmpty()) {
            lore.add(gui.line("lore.items", "count", String.valueOf(items.size())));
            int unreadable = 0;
            int shown = 0;
            for (String encoded : items) {
                ItemStack item = ItemSerializer.tryDeserialize(encoded);
                if (item == null) {
                    unreadable++;
                    continue;
                }
                if (shown < MAX_ITEM_LINES) {
                    lore.add(gui.line("lore.item-line", "amount", String.valueOf(item.getAmount()), "item", ItemNames.fragment(item)));
                }
                shown++;
            }
            if (shown > MAX_ITEM_LINES) lore.add(gui.line("lore.item-more", "count", String.valueOf(shown - MAX_ITEM_LINES)));
            if (unreadable > 0) lore.add(gui.line("lore.unreadable", "count", String.valueOf(unreadable)));
        }
        if (money > 0) lore.add(gui.line("lore.money", "money", plugin.economy().format(money)));
        if (voucherId != null) {
            VoucherDefinition voucher = plugin.vouchers().getVoucher(voucherId);
            lore.add(gui.line("lore.voucher", "voucher", Text.trusted(voucher == null ? voucherId : voucher.name())));
        }
        if (commands > 0) lore.add(gui.line("lore.commands", "count", String.valueOf(commands)));
    }

    static Component status(DonutMailPlugin plugin, MailRecord mail) {
        GuiConfig gui = plugin.gui();
        return switch (mail.state()) {
            case PENDING -> mail.codPaidAt() > 0 ? gui.line("lore.status-paid", "price", plugin.economy().format(mail.codPrice()))
                    : gui.line("lore.status-waiting");
            case COD -> gui.line("lore.status-cod");
            case CLAIMING -> gui.line("lore.locked");
            case CLAIMED -> mail.codPaidAt() > 0 ? gui.line("lore.status-paid", "price", plugin.economy().format(mail.codPrice()))
                    : gui.line("lore.status-claimed");
            case RETURNED -> gui.line("lore.status-returned", "reason",
                    Text.trusted(plugin.messages().raw("reason." + (mail.reason() == null ? "expired" : mail.reason().toLowerCase(Locale.ROOT)))));
            case EXPIRED -> gui.line("lore.status-expired");
            case DELETED -> gui.line("lore.status-deleted");
        };
    }
}
