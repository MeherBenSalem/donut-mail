package io.nightbeam.donutmail.service;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.NetworkEvent;
import io.nightbeam.donutmail.storage.StorageType;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.logging.Level;

/**
 * Several servers on one MySQL database: every change writes a small event row in the same transaction,
 * and each server reads new rows every few seconds to refresh counts and tell its online players.
 * Ownership of mail is never decided here; that is the conditional updates' job.
 */
public final class NetworkSync {
    private final DonutMailPlugin plugin;
    private long lastId;
    private int polls;

    public NetworkSync(DonutMailPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.storage().type() == StorageType.MYSQL;
    }

    public void start() {
        if (!enabled()) return;
        lastId = plugin.storage().latestEventId();
    }

    /** One poll; runs on a storage thread. */
    public synchronized void poll() {
        if (!enabled()) return;
        try {
            List<NetworkEvent> events = plugin.storage().eventsAfter(lastId, 500);
            boolean campaigns = false;
            for (NetworkEvent event : events) {
                lastId = Math.max(lastId, event.id());
                if (plugin.serverId().equals(event.origin())) continue;
                switch (event.type()) {
                    case NetworkEvent.CAMPAIGN -> campaigns = true;
                    case NetworkEvent.NEW_MAIL -> {
                        if (event.player() == null || Bukkit.getPlayer(event.player()) == null || event.mailId() == null) break;
                        MailRecord mail = plugin.storage().mail(event.mailId()).orElse(null);
                        if (mail != null && mail.state().isActive()) plugin.notifier().remoteMail(mail);
                    }
                    default -> {
                        if (event.player() != null && Bukkit.getPlayer(event.player()) != null) {
                            plugin.summaries().refresh(event.player());
                        }
                    }
                }
            }
            if (campaigns) plugin.campaigns().reload();
            if (++polls % 600 == 0) {
                plugin.storage().pruneEvents(System.currentTimeMillis() - 3_600_000L);
            }
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.FINE, "Network poll failed", failure);
        }
    }
}
