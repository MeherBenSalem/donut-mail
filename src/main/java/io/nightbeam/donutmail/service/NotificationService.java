package io.nightbeam.donutmail.service;

import io.nightbeam.donutmail.config.DonutMailConfig;
import io.nightbeam.donutmail.util.Text;
import org.bukkit.entity.Player;

public final class NotificationService {
    private final DonutMailConfig config;

    public NotificationService(DonutMailConfig config) {
        this.config = config;
    }

    public void notifyNewMail(Player player) {
        if (config.notifyChat()) {
            player.sendMessage(Text.color(config.chatFormat()));
        }
        if (config.notifyTitle()) {
            player.sendTitle(Text.color(config.titleMain()), Text.color(config.titleSub()), 10, 50, 10);
        }
        if (config.notifySound()) {
            player.playSound(player.getLocation(), config.sound(), 1.0f, 1.0f);
        }
    }
}
