package io.nightbeam.donutmail.service;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.model.Campaign;
import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.MailState;
import io.nightbeam.donutmail.model.PlayerRecord;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Mailbox counts of online players, for placeholders, the profile button and the join summary. */
public final class SummaryCache {
    private final DonutMailPlugin plugin;
    private final Map<UUID, Summary> cache = new ConcurrentHashMap<>();

    public SummaryCache(DonutMailPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @param unread    new mail (and campaigns not claimed yet)
     * @param parcels   parcels waiting (including cash on delivery)
     * @param cod       cash-on-delivery parcels waiting for payment
     * @param rewards   mail on the Rewards tab
     * @param inbox     mail on the Inbox tab (and campaigns)
     * @param campaigns campaigns not claimed yet
     */
    public record Summary(int unread, int parcels, int cod, int rewards, int inbox, int campaigns, int streak, long dailyDay) {
        public static final Summary EMPTY = new Summary(0, 0, 0, 0, 0, 0, 0, -1);

        public int waiting() {
            return parcels + rewards + inbox;
        }
    }

    public Summary get(UUID player) {
        return cache.getOrDefault(player, Summary.EMPTY);
    }

    public void remove(UUID player) {
        cache.remove(player);
    }

    /** Reloads the counts of a player who is online on this server. */
    public CompletableFuture<Summary> refresh(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return CompletableFuture.completedFuture(Summary.EMPTY);
        }
        return plugin.scheduler().supply(() -> {
            List<MailRecord> active = plugin.storage().active(uuid);
            Set<UUID> claimed = plugin.storage().claimedCampaigns(uuid);
            PlayerRecord record = plugin.storage().player(uuid).orElse(null);
            List<Campaign> visible = plugin.campaigns().visible(player, claimed, record);
            Summary summary = compute(active, visible.size(), record);
            if (Bukkit.getPlayer(uuid) != null) cache.put(uuid, summary);
            return summary;
        }).exceptionally(failure -> get(uuid));
    }

    public static Summary compute(List<MailRecord> active, int campaigns, PlayerRecord record) {
        int unread = campaigns;
        int parcels = 0;
        int cod = 0;
        int rewards = 0;
        int inbox = campaigns;
        for (MailRecord mail : active) {
            if (!mail.state().isActive()) continue;
            if (mail.readAt() == 0) unread++;
            MailKind.Tab tab = mail.kind().tab();
            if (tab == MailKind.Tab.PARCELS) {
                parcels++;
                if (mail.state() == MailState.COD) cod++;
            } else if (tab == MailKind.Tab.REWARDS) {
                rewards++;
            } else {
                inbox++;
            }
        }
        return new Summary(unread, parcels, cod, rewards, inbox, campaigns, record == null ? 0 : record.dailyStreak(),
                record == null ? -1 : record.dailyDay());
    }
}
