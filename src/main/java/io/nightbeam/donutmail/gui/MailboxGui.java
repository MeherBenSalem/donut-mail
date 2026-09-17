package io.nightbeam.donutmail.gui;

import io.nightbeam.donutmail.model.MailEntry;
import io.nightbeam.donutmail.model.VoucherDefinition;
import io.nightbeam.donutmail.service.ClaimResult;
import io.nightbeam.donutmail.service.MailService;
import io.nightbeam.donutmail.service.VoucherService;
import io.nightbeam.donutmail.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MailboxGui implements GuiView {
    public static final String TITLE = "\u1d0d\u1d00\u026a\u029f\u0299\u1d0fx";

    private final MailService mailService;
    private final VoucherService voucherService;
    private final GuiManager guiManager;
    private final UUID playerUuid;
    private final int page;
    private final Inventory inventory;
    private final List<MailEntry> pageMails;

    public MailboxGui(MailService mailService, VoucherService voucherService, GuiManager guiManager, UUID playerUuid, int page) {
        this.mailService = mailService;
        this.voucherService = voucherService;
        this.guiManager = guiManager;
        this.playerUuid = playerUuid;
        this.page = Math.max(0, page);
        this.inventory = Bukkit.createInventory(this, 54, TITLE);
        this.pageMails = render();
    }

    @Override
    public Inventory inventory() {
        return inventory;
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        if (slot <= 44) {
            int index = slot;
            if (index >= pageMails.size()) {
                return;
            }
            MailEntry mail = pageMails.get(index);
            mailService.claimMail(player, mail.getId(), result -> handleClaimResult(player, result));
            return;
        }

        if (slot == 45) {
            guiManager.openMailbox(player, Math.max(0, page - 1));
        } else if (slot == 49) {
            guiManager.openHistory(player, 0);
        } else if (slot == 53) {
            guiManager.openMailbox(player, page + 1);
        }
    }

    private List<MailEntry> render() {
        List<MailEntry> mails = mailService.getPending(playerUuid);
        int from = page * 45;
        if (from >= mails.size()) {
            decorateControls(false, page > 0);
            return List.of();
        }

        int to = Math.min(from + 45, mails.size());
        List<MailEntry> sub = new ArrayList<>(mails.subList(from, to));

        for (int i = 0; i < sub.size(); i++) {
            inventory.setItem(i, toIcon(sub.get(i)));
        }

        boolean hasNext = to < mails.size();
        boolean hasPrev = page > 0;
        decorateControls(hasNext, hasPrev);
        return sub;
    }

    private void decorateControls(boolean hasNext, boolean hasPrev) {
        inventory.setItem(45, button(hasPrev ? Material.ARROW : Material.GRAY_DYE, "&ePrevious"));
        inventory.setItem(49, button(Material.BOOK, "&bHistory"));
        inventory.setItem(53, button(hasNext ? Material.ARROW : Material.GRAY_DYE, "&eNext"));
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

    private ItemStack toIcon(MailEntry mail) {
        Material icon = Material.PAPER;
        VoucherDefinition voucher = null;
        if (mail.getVoucherId() != null && !mail.getVoucherId().isBlank()) {
            voucher = voucherService.getVoucher(mail.getVoucherId());
            if (voucher != null) {
                icon = voucher.icon();
            } else {
                icon = Material.BARRIER;
            }
        } else if (!mail.getAttachments().isEmpty()) {
            icon = Material.CHEST;
        }

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&6Mail from &f" + mail.getSender()));
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

            if (voucher != null) {
                lore.add(Text.color(" "));
                lore.add(Text.color("&6Voucher: &f" + voucher.name()));
                if (!voucher.lore().isEmpty()) {
                    for (String line : voucher.lore()) {
                        lore.add(line);
                    }
                }
            } else if (mail.getVoucherId() != null && !mail.getVoucherId().isBlank()) {
                lore.add(Text.color(" "));
                lore.add(Text.color("&cVoucher text unavailable: " + mail.getVoucherId()));
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
                lore.add(Text.color("&eAttachments: &f" + mail.getAttachments().size() + " item stack(s)"));
            }

            lore.add(Text.color("&aClick to claim"));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void handleClaimResult(Player player, ClaimResult result) {
        switch (result) {
            case SUCCESS -> {
                player.sendMessage(Text.color("&aMail claimed successfully."));
                guiManager.openMailbox(player, page);
            }
            case INVENTORY_FULL -> player.sendMessage(Text.color("&cYour inventory is full."));
            case MAIL_EXPIRED -> {
                player.sendMessage(Text.color("&eThis mail has expired."));
                guiManager.openMailbox(player, page);
            }
            case VOUCHER_MISSING -> player.sendMessage(Text.color("&cVoucher config is missing for this mail."));
            case MAIL_NOT_FOUND -> player.sendMessage(Text.color("&cMail entry no longer exists."));
        }
    }
}
