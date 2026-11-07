package io.nightbeam.donutmail.gui;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.config.GuiConfig;
import io.nightbeam.donutmail.model.Campaign;
import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.MailState;
import io.nightbeam.donutmail.service.MailService;
import io.nightbeam.donutmail.util.ItemBuilder;
import io.nightbeam.donutmail.util.ItemSerializer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The items of one mail, shown before anything is taken. For cash on delivery this is where the recipient
 * pays or declines; for the sender (Sent tab) it is where a parcel can be taken back.
 */
public final class PreviewGui extends BaseGui {
    public enum Mode { RECEIVE, COD, SENT, ADMIN, CAMPAIGN }

    private final MailRecord mail;
    private final Campaign campaign;
    private final Mode mode;
    private final Runnable back;
    private final Map<Integer, String> buttons = new HashMap<>();

    public PreviewGui(DonutMailPlugin plugin, Player player, MailRecord mail, Campaign campaign, Mode mode, Runnable back) {
        super(plugin, player);
        this.mail = mail;
        this.campaign = campaign;
        this.mode = mode;
        this.back = back;
        String sender = mail != null ? mail.senderName() : campaign.senderName();
        Component title = mode == Mode.COD ? plugin.gui().title("cod") : plugin.gui().title("preview", "sender", sender);
        this.inventory = Bukkit.createInventory(this, 54, title);
        render();
    }

    private void render() {
        GuiConfig gui = plugin.gui();
        List<String> items = mail != null ? mail.items() : campaign.items();
        List<Integer> slots = gui.slots("preview.item-slots", 54);
        for (int i = 0; i < items.size() && i < slots.size(); i++) {
            ItemStack item = ItemSerializer.tryDeserialize(items.get(i));
            if (item == null) {
                item = ItemBuilder.of(Material.BARRIER).name(gui.line("lore.unreadable", "count", "1")).build();
            }
            inventory.setItem(slots.get(i), item);
        }
        button("back");
        String sender = mail != null ? mail.senderName() : campaign.senderName();
        String price = mail == null ? "" : plugin.economy().format(mail.codPrice());
        switch (mode) {
            case COD -> {
                double balance = plugin.economy().balance(player);
                boolean canPay = plugin.economy().available() && balance >= mail.codPrice();
                if (canPay) {
                    button("pay", "price", price, "sender", sender, "balance", plugin.economy().format(balance),
                            "after", plugin.economy().format(balance - mail.codPrice()));
                } else {
                    button("pay-poor", "price", price, "balance", plugin.economy().format(balance));
                }
                info("cod-info", sender, price);
                button("decline", "sender", sender);
            }
            case RECEIVE -> {
                info("info", sender, price);
                if (mail.state() == MailState.PENDING) button(mail.hasRewards() ? "claim" : "read");
            }
            case CAMPAIGN -> {
                info("info", sender, price);
                button("claim");
            }
            case SENT -> {
                info("info", sender, price);
                if (plugin.settings().allowRecall && mail.kind() == MailKind.PARCEL && mail.codPaidAt() == 0
                        && (mail.state() == MailState.PENDING || mail.state() == MailState.COD)) {
                    button("recall");
                }
            }
            case ADMIN -> {
                info("info", sender, price);
                if (mail.state() == MailState.PENDING || mail.state() == MailState.COD) button("remove");
            }
        }
        fill();
    }

    private void button(String name, String... keyValues) {
        String path = "preview.buttons." + name;
        int slot = plugin.gui().slot(path, -1);
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, plugin.gui().button(path, keyValues).build());
        buttons.put(slot, name);
    }

    /** The info button: its configured name and lore, then the mail's text and contents. */
    private void info(String name, String sender, String price) {
        GuiConfig gui = plugin.gui();
        String path = "preview.buttons." + name;
        int slot = gui.slot(path, -1);
        if (slot < 0) return;
        ItemBuilder builder = gui.button(path, "sender", sender, "price", price);
        List<Component> lore = new ArrayList<>();
        List<Component> configured = builder.build().lore();
        if (configured != null) lore.addAll(configured);
        lore.add(gui.line("lore.separator"));
        if (mail != null) {
            MailIcons.message(gui, lore, MailService.displayMessage(mail));
            MailIcons.contents(plugin, lore, mail.items(), mail.money(), mail.voucherId(), mail.commands().size());
            if (mode == Mode.SENT || mode == Mode.ADMIN) {
                Component status = MailIcons.status(plugin, mail);
                if (status != null) {
                    lore.add(Component.empty());
                    lore.add(status);
                }
            }
        } else {
            MailIcons.message(gui, lore, campaign.message());
            MailIcons.contents(plugin, lore, campaign.items(), campaign.money(), campaign.voucherId(), campaign.commands().size());
        }
        inventory.setItem(slot, builder.lore(lore).build());
    }

    @Override
    public void click(InventoryClickEvent event) {
        event.setCancelled(true);
        String button = buttons.get(event.getRawSlot());
        if (button == null) return;
        switch (button) {
            case "back" -> {
                sound("click");
                back();
            }
            case "claim", "read" -> claim();
            case "pay" -> pay();
            case "pay-poor" -> Feedback.error(plugin, player, "cod.not-enough", "price", plugin.economy().format(mail.codPrice()));
            case "decline" -> decline();
            case "recall" -> recall();
            case "remove" -> {
                if (event.getClick().isShiftClick()) remove();
            }
            default -> {
            }
        }
    }

    private void back() {
        if (back != null) back.run();
        else player.closeInventory();
    }

    private void claim() {
        boolean started = plugin.mail().guarded(player.getUniqueId(),
                () -> campaign != null ? plugin.campaigns().claim(player, campaign) : plugin.mail().claim(player, mail.id()),
                result -> plugin.scheduler().atPlayer(player, () -> {
                    if (result != null) Feedback.claim(plugin, player, result);
                    back();
                }));
        if (!started) plugin.messages().send(player, "general.busy");
    }

    private void pay() {
        boolean started = plugin.mail().guarded(player.getUniqueId(), () -> plugin.mail().pay(player, mail.id()),
                result -> plugin.scheduler().atPlayer(player, () -> {
                    if (result != null) Feedback.pay(plugin, player, result, mail.senderName());
                    back();
                }));
        if (!started) plugin.messages().send(player, "general.busy");
    }

    private void decline() {
        boolean started = plugin.mail().guarded(player.getUniqueId(), () -> plugin.mail().decline(player, mail.id()),
                ok -> plugin.scheduler().atPlayer(player, () -> {
                    if (Boolean.TRUE.equals(ok)) {
                        plugin.messages().send(player, "cod.declined", "player", mail.senderName());
                        sound("click");
                    } else {
                        Feedback.error(plugin, player, "cod.not-waiting");
                    }
                    back();
                }));
        if (!started) plugin.messages().send(player, "general.busy");
    }

    private void recall() {
        boolean started = plugin.mail().guarded(player.getUniqueId(), () -> plugin.mail().recall(player, mail.id()),
                ok -> plugin.scheduler().atPlayer(player, () -> {
                    if (Boolean.TRUE.equals(ok)) {
                        plugin.messages().send(player, "parcels.recalled", "player", mail.receiverName() == null ? "?" : mail.receiverName());
                        sound("claim");
                    } else {
                        Feedback.error(plugin, player, "parcels.recall-failed");
                    }
                    back();
                }));
        if (!started) plugin.messages().send(player, "general.busy");
    }

    private void remove() {
        plugin.mail().adminRemove(player, mail.id()).thenAccept(result -> plugin.scheduler().atPlayer(player, () -> {
            if (result == null) {
                Feedback.error(plugin, player, "admin.remove-failed");
            } else {
                plugin.messages().send(player, "admin.removed", "what",
                        io.nightbeam.donutmail.util.Text.trusted(result.isEmpty() ? "" : plugin.messages().raw(result)));
            }
            back();
        }));
    }
}
