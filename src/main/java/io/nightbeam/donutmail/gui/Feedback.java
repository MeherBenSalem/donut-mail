package io.nightbeam.donutmail.gui;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.config.Messages;
import io.nightbeam.donutmail.service.ClaimResult;
import io.nightbeam.donutmail.service.PayResult;
import io.nightbeam.donutmail.util.Sounds;
import io.nightbeam.donutmail.util.Text;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Chat feedback for claims and payments, shared by menus and commands. */
public final class Feedback {
    private Feedback() {
    }

    public static void claim(DonutMailPlugin plugin, Player player, ClaimResult result) {
        Messages messages = plugin.messages();
        switch (result.status()) {
            case CLAIMED, PARTIAL -> {
                List<String> parts = new ArrayList<>();
                if (result.delivered() > 0) parts.add(messages.format("mailbox.what-items", "count", String.valueOf(result.delivered())));
                if (result.money() > 0) parts.add(messages.format("mailbox.what-money", "money", plugin.economy().format(result.money())));
                if (result.rewards()) parts.add(messages.raw("mailbox.what-rewards"));
                if (parts.isEmpty()) parts.add(messages.raw("mailbox.what-message"));
                String what = String.join("&a, ", parts);
                messages.send(player, "mailbox.claimed", "what", Text.trusted(what));
                if (result.status() == ClaimResult.Status.PARTIAL) {
                    messages.send(player, "mailbox.partial", "left", String.valueOf(result.left()));
                }
                if (result.unreadable() > 0) messages.send(player, "mailbox.unreadable", "count", String.valueOf(result.unreadable()));
                Sounds.play(player, plugin.settings().raw, "claim");
            }
            case INVENTORY_FULL -> {
                if (result.unreadable() > 0 && result.unreadable() == result.left()) {
                    messages.send(player, "mailbox.unreadable", "count", String.valueOf(result.unreadable()));
                } else {
                    messages.send(player, "mailbox.inventory-full");
                }
                Sounds.play(player, plugin.settings().raw, "error");
            }
            case GONE -> error(plugin, player, "mailbox.gone");
            case EXPIRED -> error(plugin, player, "mailbox.expired");
            case NEEDS_ECONOMY -> error(plugin, player, "mailbox.needs-economy");
            case VOUCHER_MISSING -> error(plugin, player, "mailbox.voucher-missing");
            case ERROR -> error(plugin, player, "general.storage-error");
            case WAITING_PAYMENT, OFFLINE -> {
            }
        }
    }

    public static void pay(DonutMailPlugin plugin, Player player, PayResult result, String seller) {
        Messages messages = plugin.messages();
        switch (result.status()) {
            case PAID -> {
                messages.send(player, "cod.paid", "price", plugin.economy().format(result.price()), "player", seller);
                Sounds.play(player, plugin.settings().raw, "paid");
                if (result.claim() != null && result.claim().status() != ClaimResult.Status.CLAIMED) {
                    claim(plugin, player, result.claim());
                }
            }
            case NOT_ENOUGH -> error(plugin, player, "cod.not-enough", "price", plugin.economy().format(result.price()));
            case NO_ECONOMY -> error(plugin, player, "general.economy-missing");
            case NOT_WAITING -> error(plugin, player, "cod.not-waiting");
            case FAILED -> error(plugin, player, "cod.payment-failed");
            case ERROR -> error(plugin, player, "general.storage-error");
        }
    }

    public static void error(DonutMailPlugin plugin, Player player, String key, String... keyValues) {
        plugin.messages().send(player, key, keyValues);
        Sounds.play(player, plugin.settings().raw, "error");
    }
}
