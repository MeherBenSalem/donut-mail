package io.nightbeam.donutmail.service;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.model.Campaign;
import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.PlayerRecord;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Server campaigns: one stored mail for every player, including players who join later, inside an optional
 * time window. A player's claim is recorded once (a unique row per player) together with their own copy,
 * which is then claimed like any other mail.
 */
public final class CampaignService {
    private final DonutMailPlugin plugin;
    private volatile List<Campaign> campaigns = List.of();
    private final Set<UUID> announced = ConcurrentHashMap.newKeySet();
    private volatile boolean seeded;

    public CampaignService(DonutMailPlugin plugin) {
        this.plugin = plugin;
    }

    public List<Campaign> all() {
        return campaigns;
    }

    /** Reloads the list (storage thread) and announces campaigns that just started. */
    public void reload() {
        List<Campaign> loaded = plugin.storage().campaigns();
        this.campaigns = List.copyOf(loaded);
        long now = System.currentTimeMillis();
        if (!seeded) {
            loaded.stream().filter(c -> c.isLive(now) || c.isOver(now)).forEach(c -> announced.add(c.id()));
            seeded = true;
            return;
        }
        for (Campaign campaign : loaded) {
            if (campaign.isLive(now) && announced.add(campaign.id())) {
                plugin.notifier().campaignStarted(campaign);
            }
        }
    }

    public CompletableFuture<Void> reloadAsync() {
        return plugin.scheduler().run(this::reload).exceptionally(failure -> {
            plugin.getLogger().log(Level.WARNING, "Could not load campaigns", failure);
            return null;
        });
    }

    public boolean canSee(Player player, Campaign campaign) {
        if (!campaign.isLive(System.currentTimeMillis())) return false;
        String global = plugin.settings().campaignPermission;
        if (global != null && !global.isBlank() && !player.hasPermission(global)) return false;
        return campaign.permission() == null || player.hasPermission(campaign.permission());
    }

    /** Live campaigns this player can see and has not claimed. */
    public List<Campaign> visible(Player player, Set<UUID> claimed, PlayerRecord record) {
        List<Campaign> out = new ArrayList<>();
        for (Campaign campaign : campaigns) {
            if (claimed.contains(campaign.id()) || !canSee(player, campaign)) continue;
            if (campaign.existingOnly() && !knownBefore(player, record, campaign.createdAt())) continue;
            out.add(campaign);
        }
        return out;
    }

    private static boolean knownBefore(Player player, PlayerRecord record, long time) {
        if (record != null && record.firstSeen() > 0 && record.firstSeen() < time) return true;
        long firstPlayed = player.getFirstPlayed();
        return firstPlayed > 0 && firstPlayed < time;
    }

    public Optional<Campaign> byId(UUID id) {
        return campaigns.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    /** Saves a new campaign. False when the name is taken. */
    public CompletableFuture<Boolean> create(Campaign campaign) {
        return plugin.scheduler().supply(() -> {
            if (!plugin.storage().insertCampaign(campaign)) return false;
            plugin.audit().log("CAMPAIGN", "name", campaign.name(), "by", campaign.createdBy(), "items", campaign.items().size(),
                    "money", campaign.money(), "voucher", campaign.voucherId(), "starts", campaign.startsAt(), "ends", campaign.endsAt(),
                    "existingOnly", campaign.existingOnly());
            reload();
            plugin.discord().campaign(campaign);
            return true;
        });
    }

    /** Claims a campaign for a player: records the claim and their copy in one transaction, then claims the copy. */
    public CompletableFuture<ClaimResult> claim(Player player, Campaign campaign) {
        long now = System.currentTimeMillis();
        MailRecord copy = plugin.mail().serverMail(MailKind.CAMPAIGN, player.getUniqueId(), campaign.senderName())
                .receiverName(player.getName()).message(campaign.message()).items(campaign.items()).money(campaign.money())
                .voucherId(campaign.voucherId()).commands(campaign.commands()).campaignId(campaign.id()).build();
        return plugin.scheduler().supply(() -> plugin.storage().claimCampaign(campaign.id(), player.getUniqueId(), copy, now))
                .thenCompose(won -> {
                    if (!won) return CompletableFuture.completedFuture(ClaimResult.of(ClaimResult.Status.GONE));
                    plugin.audit().log("CAMPAIGN_CLAIM", "name", campaign.name(), "player", player.getName(), "copy", copy.id());
                    return plugin.mail().claim(player, copy.id());
                });
    }
}
