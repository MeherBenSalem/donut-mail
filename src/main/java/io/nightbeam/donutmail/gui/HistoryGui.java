package io.nightbeam.donutmail.gui;

import io.nightbeam.donutmail.model.MailEntry;
import io.nightbeam.donutmail.model.MailStatus;
import io.nightbeam.donutmail.model.VoucherDefinition;
import io.nightbeam.donutmail.service.VoucherService;
import io.nightbeam.donutmail.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class HistoryGui implements GuiView {
    public static final String TITLE = "\u1d0d\u1d00\u026a\u029f\u0299\u1d0fx \u2022 \u029c\u026a\ua731\u1d1b\u1d0f\u0280\u028f";

    private final GuiManager guiManager;
    private final VoucherService voucherService;
    private final int page;
    private final List<MailEntry> history;
    private final Inventory inventory;

    public HistoryGui(GuiManager guiManager, VoucherService voucherService, UUID playerUuid, int page, List<MailEntry> history) {
        this.guiManager = guiManager;
        this.voucherService = voucherService;
        this.page = Math.max(0, page);
        this.history = history;
        this.inventory = Bukkit.createInventory(this, 54, TITLE);
        render();
    }

    @Override
    public Inventory inventory() {
        return inventory;
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 45) {
            guiManager.openHistory(player, Math.max(0, page - 1));
        } else if (slot == 49) {
            guiManager.openMailbox(player, 0);
        } else if (slot == 53) {
            guiManager.openHistory(player, page + 1);
        }
    }

    private void render() {
        int from = page * 45;
        int to = Math.min(history.size(), from + 45);

        if (from < history.size()) {
            for (int i = from; i < to; i++) {
                inventory.setItem(i - from, icon(history.get(i)));
            }
        }

        inventory.setItem(45, button(from > 0 ? Material.ARROW : Material.GRAY_DYE, "&ePrevious"));
        inventory.setItem(49, button(Material.CHEST, "&bBack to Mailbox"));
        inventory.setItem(53, button(to < history.size() ? Material.ARROW : Material.GRAY_DYE, "&eNext"));
    }

    private ItemStack button(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack icon(MailEntry mail) {
        Material material = mail.getStatus() == MailStatus.CLAIMED ? Material.LIME_DYE : Material.GRAY_DYE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&7From &f" + mail.getSender()));
            List<String> lore = new ArrayList<>();
            lore.add(Text.color("&eMessage:"));
            List<String> messageLines = Text.toLoreLines(mail.getMessage(), 44);
            if (messageLines.isEmpty()) {
                lore.add(Text.color("&7(no message)"));
            } else {
                for (String line : messageLines) {
                    lore.add(Text.color("&f" + line));
                }
            }

            if (mail.getVoucherId() != null && !mail.getVoucherId().isBlank()) {
                VoucherDefinition voucher = voucherService.getVoucher(mail.getVoucherId());
                lore.add(Text.color(" "));
                if (voucher != null) {
                    lore.add(Text.color("&6Voucher: &f" + voucher.name()));
                    if (!voucher.lore().isEmpty()) {
                        for (String line : voucher.lore()) {
                            lore.add(line);
                        }
                    }
                } else {
                    lore.add(Text.color("&cVoucher text unavailable: " + mail.getVoucherId()));
                }
            }

            if (!mail.getConsoleCommands().isEmpty()) {
                lore.add(Text.color(" "));
                lore.add(Text.color("&eCommands:"));
                for (String command : mail.getConsoleCommands()) {
                    lore.add(Text.color("&7- &f" + command));
                }
            }

            if (!mail.getAttachments().isEmpty()) {
                lore.add(Text.color(" "));
                lore.add(Text.color("&eAttachments received: &f" + mail.getAttachments().size() + " item stack(s)"));
            }

            lore.add(Text.color(" "));
            lore.add(Text.color("&8State: &f" + mail.getStatus().name()));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
