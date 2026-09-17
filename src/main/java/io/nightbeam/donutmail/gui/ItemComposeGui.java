package io.nightbeam.donutmail.gui;

import io.nightbeam.donutmail.scheduler.SchedulerAdapter;
import io.nightbeam.donutmail.service.MailService;
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

public final class ItemComposeGui implements GuiView {
    public static final String TITLE = "\u1d0d\u1d00\u026a\u029f\u0299\u1d0fx \u2022 \u1d05\u1d07\u1d0c\u1d0f\u1d0d\u1d18\u1d0f\ua731\u1d07";

    private final SchedulerAdapter scheduler;
    private final MailService mailService;
    private final UUID targetUuid;
    private final String targetName;
    private final String senderName;
    private final Inventory inventory;
    private boolean completed;

    public ItemComposeGui(SchedulerAdapter scheduler, MailService mailService, UUID targetUuid, String targetName, String senderName) {
        this.scheduler = scheduler;
        this.mailService = mailService;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.senderName = senderName;
        this.inventory = Bukkit.createInventory(this, 54, TITLE);
        decorateBottomRow();
    }

    @Override
    public Inventory inventory() {
        return inventory;
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        int raw = event.getRawSlot();
        if (raw < 0) {
            return;
        }

        if (raw >= inventory.getSize()) {
            event.setCancelled(false);
            return;
        }

        if (raw < 45) {
            event.setCancelled(false);
            return;
        }

        event.setCancelled(true);

        if (raw == 49) {
            submit(player);
            return;
        }

        if (raw == 45) {
            completed = true;
            returnItems(player);
            player.closeInventory();
        }
    }

    public void onClose(Player player) {
        if (!completed) {
            returnItems(player);
        }
    }

    private void submit(Player sender) {
        List<ItemStack> items = collectItems();
        if (items.isEmpty()) {
            sender.sendMessage(Text.color("&cAdd at least one item before sending."));
            return;
        }

        completed = true;
        mailService.sendMail(
                targetUuid,
                senderName,
                "&6Item Delivery\n\n&fYou received item attachments.",
                items,
                null,
                List.of()
        ).thenAccept(id -> scheduler.runAtPlayer(sender, () -> {
            sender.sendMessage(Text.color("&aSent " + items.size() + " item stack(s) to &f" + targetName + "&a."));
            sender.closeInventory();
        }));
    }

    private List<ItemStack> collectItems() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            out.add(item.clone());
            inventory.setItem(i, null);
        }
        return out;
    }

    private void returnItems(Player player) {
        for (int i = 0; i < 45; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            inventory.setItem(i, null);
            player.getInventory().addItem(item).values().forEach(overflow ->
                    player.getWorld().dropItemNaturally(player.getLocation(), overflow)
            );
        }
    }

    private void decorateBottomRow() {
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, button(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        inventory.setItem(45, button(Material.BARRIER, "&cCancel"));
        inventory.setItem(49, button(Material.LIME_CONCRETE, "&aSend Items to &f" + targetName));
        inventory.setItem(53, button(Material.PAPER, "&7Place items in top 5 rows"));
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
}
