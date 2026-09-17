package io.nightbeam.donutmail.listener;

import io.nightbeam.donutmail.service.MailService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerJoinListener implements Listener {
    private final MailService mailService;

    public PlayerJoinListener(MailService mailService) {
        this.mailService = mailService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        mailService.notifyIfHasPending(event.getPlayer());
    }
}
