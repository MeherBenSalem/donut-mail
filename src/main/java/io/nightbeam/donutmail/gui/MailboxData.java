package io.nightbeam.donutmail.gui;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.model.Campaign;
import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.MailState;
import io.nightbeam.donutmail.model.PlayerRecord;
import io.nightbeam.donutmail.service.SummaryCache;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** A snapshot of one player's mailbox, loaded off the main thread before a menu opens or refreshes. */
public record MailboxData(List<MailRecord> active, List<Campaign> campaigns, PlayerRecord record, List<MailRecord> sent,
                          int blocked, SummaryCache.Summary summary) {

    public static CompletableFuture<MailboxData> load(DonutMailPlugin plugin, Player player, MailKind.Tab tab) {
        UUID uuid = player.getUniqueId();
        return plugin.scheduler().supply(() -> {
            List<MailRecord> active = plugin.storage().active(uuid);
            Set<UUID> claimed = plugin.storage().claimedCampaigns(uuid);
            PlayerRecord record = plugin.storage().player(uuid).orElse(null);
            List<Campaign> campaigns = plugin.campaigns().visible(player, claimed, record);
            List<MailRecord> sent = tab == MailKind.Tab.SENT ? plugin.storage().sent(uuid, 270) : List.of();
            int blocked = plugin.storage().blocks(uuid).size();
            return new MailboxData(active, campaigns, record, sent, blocked, SummaryCache.compute(active, campaigns.size(), record));
        });
    }

    /** What a tab lists, in order. */
    public List<Object> entries(MailKind.Tab tab) {
        List<Object> out = new ArrayList<>();
        if (tab == MailKind.Tab.SENT) {
            out.addAll(sent);
            return out;
        }
        if (tab == MailKind.Tab.INBOX) {
            List<Campaign> sorted = new ArrayList<>(campaigns);
            sorted.sort(Comparator.comparingLong(Campaign::createdAt).reversed());
            out.addAll(sorted);
        }
        List<MailRecord> mails = new ArrayList<>();
        for (MailRecord mail : active) {
            if (mail.kind().tab() == tab) mails.add(mail);
        }
        // Waiting for payment first, then newest first.
        mails.sort(Comparator.comparing((MailRecord m) -> m.state() != MailState.COD)
                .thenComparing(Comparator.comparingLong(MailRecord::createdAt).reversed()));
        out.addAll(mails);
        return out;
    }

    public int count(MailKind.Tab tab) {
        return entries(tab).size();
    }

    public boolean acceptsParcels() {
        return record == null || record.acceptParcels();
    }
}
