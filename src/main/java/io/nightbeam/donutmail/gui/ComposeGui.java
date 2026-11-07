package io.nightbeam.donutmail.gui;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.config.GuiConfig;
import io.nightbeam.donutmail.config.Settings;
import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.MailState;
import io.nightbeam.donutmail.service.ParcelService;
import io.nightbeam.donutmail.service.SendResult;
import io.nightbeam.donutmail.util.Durations;
import io.nightbeam.donutmail.util.ItemSerializer;
import io.nightbeam.donutmail.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writing a parcel: drop items in, pick the recipient, add a note and an optional cash-on-delivery price,
 * then send. Items in this menu always go somewhere: into the parcel, back to the player's inventory, or
 * (if that is full) into their own mailbox. They are never dropped.
 */
public final class ComposeGui extends BaseGui {
    private final Set<Integer> itemSlots;
    private final Map<Integer, String> buttons = new HashMap<>();
    private ParcelService.Recipient recipient;
    private double codPrice;
    private String note = "";
    /** The items left this menu (sent, returned, or held while the player types in chat). */
    private boolean released;
    private boolean prompting;
    private boolean sending;

    public ComposeGui(DonutMailPlugin plugin, Player player, ParcelService.Recipient recipient, double codPrice) {
        super(plugin, player);
        this.recipient = recipient;
        this.codPrice = codPrice;
        this.inventory = Bukkit.createInventory(this, 54, plugin.gui().title("compose"));
        this.itemSlots = new java.util.TreeSet<>(plugin.gui().slots("compose.item-slots", 54));
        renderButtons();
    }

    private void renderButtons() {
        GuiConfig gui = plugin.gui();
        for (Integer slot : buttons.keySet()) inventory.setItem(slot, null);
        buttons.clear();
        String name = recipient == null ? "?" : recipient.name();
        if (recipient == null) {
            button("recipient-none");
        } else {
            ItemStack head = gui.button("compose.buttons.recipient", "player", name)
                    .owner(Bukkit.getOfflinePlayer(recipient.id())).build();
            place("recipient", head);
        }
        button("cancel");
        button("note", "note", note.isEmpty() ? Text.trusted(gui.text("compose.buttons.note-empty")) : "\"" + note + "\"");
        int stacks = countItems();
        String priceText = codPrice > 0 ? plugin.economy().format(codPrice) : Text.trusted(gui.text("compose.buttons.cod-off"));
        if (plugin.settings().codEnabled && player.hasPermission("donutmail.cod")) {
            button("cod", "price", priceText);
        }
        double fee = plugin.parcels().postage(Math.max(1, stacks), player);
        button("send", "player", name, "count", String.valueOf(stacks),
                "cod", codPrice > 0 ? Text.trusted(Text.substitute(gui.text("compose.buttons.cod-line"), "price", plugin.economy().format(codPrice))) : "",
                "postage", fee > 0 ? Text.trusted(Text.substitute(gui.text("compose.buttons.postage-line"), "fee", plugin.economy().format(fee))) : "");
        button("help", "days", String.valueOf(plugin.settings().parcelExpireDays));
        ItemStack pane = gui.filler();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!itemSlots.contains(slot) && inventory.getItem(slot) == null && pane != null) inventory.setItem(slot, pane);
        }
    }

    private void button(String name, String... keyValues) {
        String path = "compose.buttons." + name;
        place(name, plugin.gui().button(path, keyValues).build());
    }

    private void place(String name, ItemStack item) {
        int slot = plugin.gui().slot("compose.buttons." + name, -1);
        if (slot < 0 || slot >= inventory.getSize() || itemSlots.contains(slot)) return;
        inventory.setItem(slot, item);
        buttons.put(slot, name.equals("recipient-none") ? "recipient" : name);
    }

    private int countItems() {
        int count = 0;
        for (int slot : itemSlots) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) count++;
        }
        return count;
    }

    @Override
    public void click(InventoryClickEvent event) {
        int raw = event.getRawSlot();
        if (released || sending) {
            event.setCancelled(true);
            return;
        }
        if (raw >= inventory.getSize()) {
            // The player's own inventory: allowed, except collecting stacks that could pull menu buttons.
            switch (event.getAction()) {
                case COLLECT_TO_CURSOR -> event.setCancelled(true);
                default -> {
                }
            }
            return;
        }
        if (itemSlots.contains(raw)) {
            if (event.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR) event.setCancelled(true);
            plugin.scheduler().atPlayerLater(player, this::refreshSendButton, 1L);
            return;
        }
        event.setCancelled(true);
        String button = buttons.get(raw);
        if (button == null) return;
        switch (button) {
            case "cancel" -> {
                sound("click");
                player.closeInventory();
            }
            case "recipient" -> askRecipient();
            case "note" -> askNote();
            case "cod" -> {
                if (event.getClick().isRightClick()) {
                    codPrice = 0;
                    sound("click");
                    renderButtons();
                } else {
                    askPrice();
                }
            }
            case "send" -> send();
            default -> {
            }
        }
    }

    private void refreshSendButton() {
        if (!released && isOpen()) renderButtons();
    }

    @Override
    public void drag(InventoryDragEvent event) {
        if (released || sending) {
            event.setCancelled(true);
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < inventory.getSize() && !itemSlots.contains(raw)) {
                event.setCancelled(true);
                return;
            }
        }
        plugin.scheduler().atPlayerLater(player, this::refreshSendButton, 1L);
    }

    @Override
    public void close(InventoryCloseEvent event) {
        if (prompting || released || sending) return;
        released = true;
        giveBack(takeItems(), false);
    }

    /** The menu closed while limits were being checked: the items go back now. */
    private void releaseAfterCheck(boolean mailboxOnly) {
        sending = false;
        if (released) return;
        released = true;
        giveBack(takeItems(), mailboxOnly);
    }

    // ── Chat input ──────────────────────────────────────────────────────────

    private void prompt(String key, java.util.function.Consumer<String> answer, String... keyValues) {
        prompting = true;
        plugin.guiManager().prompts().ask(player, key, text -> {
            prompting = false;
            answer.accept(text);
        }, end -> {
            prompting = false;
            if (end == ChatPrompts.End.LEFT || end == ChatPrompts.End.SHUTDOWN) {
                released = true;
                giveBack(takeItems(), end == ChatPrompts.End.SHUTDOWN);
            } else {
                reopen();
            }
        }, keyValues);
    }

    private void reopen() {
        if (released) return;
        renderButtons();
        player.openInventory(inventory);
    }

    private void askRecipient() {
        prompt("parcels.prompt-recipient", text -> plugin.parcels().resolve(text).thenAccept(found ->
                plugin.scheduler().atPlayer(player, () -> {
                    if (found.isEmpty()) {
                        Feedback.error(plugin, player, "general.unknown-player", "player", text);
                    } else {
                        recipient = found.get();
                        sound("click");
                    }
                    reopen();
                }, () -> {
                    released = true;
                    giveBack(takeItems(), true);
                })));
    }

    private void askNote() {
        int max = plugin.settings().noteMaxLength;
        prompt("parcels.prompt-note", text -> {
            if (text.equalsIgnoreCase(plugin.messages().raw("general.clear-word"))) {
                note = "";
            } else if (max > 0 && text.length() > max) {
                Feedback.error(plugin, player, "parcels.note-too-long", "max", String.valueOf(max));
            } else {
                note = text;
                sound("click");
            }
            reopen();
        }, "max", String.valueOf(max));
    }

    private void askPrice() {
        Settings settings = plugin.settings();
        if (!plugin.economy().available()) {
            Feedback.error(plugin, player, "general.economy-missing");
            return;
        }
        prompt("parcels.prompt-price", text -> {
            double price = Durations.parseAmount(text);
            if (price == 0) {
                codPrice = 0;
            } else if (Double.isNaN(price) || price < settings.codMin || price > settings.codMax) {
                Feedback.error(plugin, player, "parcels.price-invalid", "min", plugin.economy().format(settings.codMin),
                        "max", plugin.economy().format(settings.codMax));
            } else {
                codPrice = price;
                sound("click");
            }
            reopen();
        });
    }

    // ── Sending ─────────────────────────────────────────────────────────────

    private void send() {
        if (recipient == null) {
            Feedback.error(plugin, player, "parcels.no-recipient");
            return;
        }
        if (countItems() == 0) {
            Feedback.error(plugin, player, "parcels.empty");
            return;
        }
        List<ItemStack> preview = new ArrayList<>();
        for (int slot : itemSlots) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) preview.add(item);
        }
        ItemStack banned = plugin.parcels().blacklisted(preview);
        if (banned != null) {
            Feedback.error(plugin, player, "parcels.blacklisted", "item", io.nightbeam.donutmail.util.ItemNames.fragment(banned));
            return;
        }
        sending = true;
        // Check limits, blocks and the toggle before taking anything out of the menu.
        plugin.parcels().check(player, recipient, codPrice).thenAccept(check -> plugin.scheduler().atPlayer(player, () -> {
            if (released || !isOpen()) {
                releaseAfterCheck(false);
                return;
            }
            if (!check.sent()) {
                sending = false;
                explain(check);
                return;
            }
            List<ItemStack> items = takeItems();
            ParcelService.Recipient to = recipient;
            double price = codPrice;
            plugin.parcels().send(player, to, items, note, price).thenAccept(result -> plugin.scheduler().atPlayer(player, () -> {
                if (result.sent()) {
                    released = true;
                    plugin.messages().send(player, price > 0 ? "parcels.sent-cod" : "parcels.sent", "player", to.name(),
                            "price", plugin.economy().format(price));
                    if (!result.detail().isEmpty()) plugin.messages().send(player, "parcels.postage-paid", "fee", result.detail());
                    sound("send");
                    player.closeInventory();
                } else {
                    // Put everything back where it was and say why.
                    sending = false;
                    restore(items);
                    explain(result);
                }
            }, () -> {
                if (!result.sent()) giveBack(items, true);
            }));
        }, () -> releaseAfterCheck(true)));
    }

    private void explain(SendResult result) {
        String name = recipient == null ? "?" : recipient.name();
        switch (result.status()) {
            case DISABLED -> Feedback.error(plugin, player, "parcels.disabled");
            case SELF -> Feedback.error(plugin, player, "parcels.self");
            case UNKNOWN_RECIPIENT -> Feedback.error(plugin, player, "general.unknown-player", "player", name);
            case BLOCKED -> Feedback.error(plugin, player, "parcels.blocked", "player", name);
            case REFUSING -> Feedback.error(plugin, player, "parcels.refusing", "player", name);
            case COOLDOWN -> Feedback.error(plugin, player, "parcels.cooldown", "seconds", result.detail());
            case DAILY_LIMIT -> Feedback.error(plugin, player, "parcels.daily-limit", "limit", result.detail());
            case RECIPIENT_LIMIT -> Feedback.error(plugin, player, "parcels.recipient-limit", "limit", result.detail(), "player", name);
            case MAILBOX_FULL -> Feedback.error(plugin, player, "parcels.mailbox-full", "player", name);
            case BLACKLISTED -> Feedback.error(plugin, player, "parcels.blacklisted", "item", result.detail());
            case EMPTY -> Feedback.error(plugin, player, "parcels.empty");
            case COD_DISABLED -> Feedback.error(plugin, player, "parcels.cod-disabled");
            case COD_PRICE -> Feedback.error(plugin, player, "parcels.price-invalid", "min", plugin.economy().format(plugin.settings().codMin),
                    "max", plugin.economy().format(plugin.settings().codMax));
            case NO_ECONOMY -> Feedback.error(plugin, player, "general.economy-missing");
            case POSTAGE -> Feedback.error(plugin, player, "parcels.postage-insufficient", "fee", result.detail());
            case CANCELLED -> {
                if (result.detail().isEmpty()) Feedback.error(plugin, player, "parcels.cancelled");
                else player.sendMessage(Text.parse(result.detail()));
            }
            case ERROR -> Feedback.error(plugin, player, "parcels.failed");
            case SENT -> {
            }
        }
    }

    /** Takes every item out of the menu (on the player's thread). */
    private List<ItemStack> takeItems() {
        List<ItemStack> items = new ArrayList<>();
        for (int slot : itemSlots) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
                inventory.setItem(slot, null);
            }
        }
        return items;
    }

    /** After a failed send: back into the menu if it is still open, otherwise back to the player. */
    private void restore(List<ItemStack> items) {
        if (!released && isOpen()) {
            List<ItemStack> left = new ArrayList<>(items);
            for (int slot : itemSlots) {
                if (left.isEmpty()) break;
                ItemStack current = inventory.getItem(slot);
                if (current == null || current.getType().isAir()) inventory.setItem(slot, left.remove(0));
            }
            renderButtons();
            if (!left.isEmpty()) giveBack(left, false);
            return;
        }
        released = true;
        giveBack(items, !player.isOnline());
    }

    /**
     * Returns items to the player's inventory; what does not fit goes into their own mailbox (never on the
     * ground). {@code mailboxOnly} skips the inventory (the player is leaving or the server is stopping).
     */
    private void giveBack(List<ItemStack> items, boolean mailboxOnly) {
        if (items.isEmpty()) return;
        List<ItemStack> overflow = new ArrayList<>();
        if (mailboxOnly) {
            overflow.addAll(items);
        } else {
            for (ItemStack item : items) {
                overflow.addAll(player.getInventory().addItem(item).values());
            }
        }
        if (overflow.isEmpty()) return;
        long now = System.currentTimeMillis();
        MailRecord back = MailRecord.builder().kind(MailKind.RETURN).receiver(player.getUniqueId()).receiverName(player.getName())
                .senderId(player.getUniqueId()).senderName(player.getName())
                .message(plugin.messages().format("mail.returned-compose")).items(ItemSerializer.serializeAll(overflow))
                .createdAt(now).updatedAt(now).expiresAt(0).state(MailState.PENDING).build();
        plugin.scheduler().run(() -> plugin.storage().insert(back)).whenComplete((ignored, failure) -> {
            if (failure != null) {
                plugin.getLogger().severe("Could not keep " + overflow.size() + " stack(s) from " + player.getName()
                        + "'s parcel menu: " + failure.getMessage() + " " + ItemSerializer.serializeAll(overflow));
            } else {
                plugin.audit().log("COMPOSE_RETURN", "player", player.getName(), "stacks", overflow.size(), "mail", back.id());
            }
        });
        if (!mailboxOnly && player.isOnline()) {
            plugin.messages().send(player, "parcels.returned-to-mailbox", "count", String.valueOf(overflow.size()));
        }
    }
}
