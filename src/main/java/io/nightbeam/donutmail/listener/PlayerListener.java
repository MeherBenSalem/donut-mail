package io.nightbeam.donutmail.listener;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.model.PlayerRecord;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.logging.Level;

/** On join: record the player, finish interrupted claims, deliver the daily reward, then show the summary. */
public final class PlayerListener implements Listener {
    private final DonutMailPlugin plugin;

    public PlayerListener(DonutMailPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        joined(plugin, event.getPlayer());
    }

    /** Also used for players already online after a reload. */
    public static void joined(DonutMailPlugin plugin, Player player) {
        plugin.knownNames().add(player.getName());
        plugin.scheduler().supply(() -> plugin.storage().touchPlayer(player.getUniqueId(), player.getName(), System.currentTimeMillis()))
                .thenAccept(record -> {
                    plugin.mail().recover(player);
                    deliverDaily(plugin, player, record);
                    plugin.notifier().joinSummary(player);
                })
                .exceptionally(failure -> {
                    plugin.getLogger().log(Level.WARNING, "Could not load the mailbox of " + player.getName(), failure);
                    return null;
                });
    }

    private static void deliverDaily(DonutMailPlugin plugin, Player player, PlayerRecord record) {
        try {
            plugin.dailyRewards().onJoin(player, record);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not deliver the daily reward of " + player.getName(), failure);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.summaries().remove(event.getPlayer().getUniqueId());
    }
}
