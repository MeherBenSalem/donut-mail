package io.nightbeam.donutmail.gui;

import io.nightbeam.donutmail.scheduler.SchedulerAdapter;
import io.nightbeam.donutmail.service.MailService;
import io.nightbeam.donutmail.service.VoucherService;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class GuiManager {
    private final SchedulerAdapter scheduler;
    private final MailService mailService;
    private final VoucherService voucherService;

    public GuiManager(SchedulerAdapter scheduler, MailService mailService, VoucherService voucherService) {
        this.scheduler = scheduler;
        this.mailService = mailService;
        this.voucherService = voucherService;
    }

    public void openMailbox(Player player, int page) {
        scheduler.runAtPlayer(player, () -> {
            MailboxGui gui = new MailboxGui(mailService, voucherService, this, player.getUniqueId(), page);
            player.openInventory(gui.inventory());
        });
    }

    public void openHistory(Player player, int page) {
        scheduler.runAtPlayer(player, () -> {
            HistoryGui gui = new HistoryGui(this, voucherService, player.getUniqueId(), page, mailService.getHistory(player.getUniqueId()));
            player.openInventory(gui.inventory());
        });
    }

    public void openItemCompose(Player player, UUID targetUuid, String targetName) {
        scheduler.runAtPlayer(player, () -> {
            ItemComposeGui gui = new ItemComposeGui(scheduler, mailService, targetUuid, targetName, player.getName());
            player.openInventory(gui.inventory());
        });
    }
}
