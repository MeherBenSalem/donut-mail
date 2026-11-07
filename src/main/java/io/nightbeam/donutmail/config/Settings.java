package io.nightbeam.donutmail.config;

import io.nightbeam.donutmail.storage.StorageType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Values from config.yml, read once per load or reload. */
public final class Settings {
    public final YamlConfiguration raw;

    private final StorageType storageType;
    private final String mysqlUrl;
    private final String mysqlUser;
    private final String mysqlPassword;
    private final int mysqlPoolSize;

    public final String serverIdSetting;
    public final int pollSeconds;

    public final int mailExpireDays;
    public final int expiryScanSeconds;
    public final int historyRetentionDays;

    public final boolean parcelsEnabled;
    public final int parcelExpireDays;
    public final boolean returnToSender;
    public final boolean allowRecall;
    public final boolean allowSelf;
    public final int cooldownSeconds;
    public final int maxPerDay;
    public final int maxPendingPerRecipient;
    public final int mailboxCapacity;
    public final int noteMaxLength;
    public final Set<Material> blacklist;
    public final boolean postageEnabled;
    public final double postageFlat;
    public final double postagePerStack;

    public final boolean codEnabled;
    public final double codMin;
    public final double codMax;
    public final double codTaxPercent;

    public final boolean dropOverflow;

    public final boolean joinSummary;
    public final int joinDelaySeconds;
    public final boolean liveNotices;
    public final boolean notifyChat;
    public final boolean notifyTitle;
    public final boolean notifyActionBar;
    public final boolean notifySound;
    public final String notifySoundKey;

    public final boolean announceCampaigns;
    public final String campaignPermission;

    public final boolean codesEnabled;
    public final int codeAttemptCooldown;

    public final boolean votesEnabled;
    public final String voteMessage;
    public final double voteMoney;
    public final List<String> voteItems;
    public final List<String> voteCommands;
    public final String voteVoucher;

    public final boolean discordEnabled;
    public final String discordUrl;
    public final String discordUsername;
    public final String discordAvatar;
    public final String discordColor;

    public final boolean mailboxBlocks;
    public final List<String> mailboxLocations;

    public final boolean audit;
    public final boolean placeholderApi;

    public Settings(YamlConfiguration config) {
        this.raw = config;
        this.storageType = StorageType.fromString(config.getString("storage.type", "SQLITE"));
        String host = config.getString("storage.mysql.host", "localhost");
        int port = config.getInt("storage.mysql.port", 3306);
        String database = config.getString("storage.mysql.database", "donutmail");
        String parameters = config.getString("storage.mysql.parameters",
                "useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&serverTimezone=UTC");
        this.mysqlUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + (parameters == null || parameters.isBlank() ? "" : "?" + parameters);
        this.mysqlUser = config.getString("storage.mysql.username", "root");
        this.mysqlPassword = config.getString("storage.mysql.password", "");
        this.mysqlPoolSize = Math.max(2, config.getInt("storage.mysql.pool-size", 10));

        this.serverIdSetting = config.getString("network.server-id", "");
        this.pollSeconds = Math.max(1, config.getInt("network.poll-interval-seconds", 3));

        this.mailExpireDays = Math.max(0, config.getInt("mail.expire-days", 30));
        this.expiryScanSeconds = Math.max(5, config.getInt("mail.expiry-scan-seconds", 60));
        this.historyRetentionDays = Math.max(0, config.getInt("mail.history-retention-days", 60));

        this.parcelsEnabled = config.getBoolean("parcels.enabled", true);
        this.parcelExpireDays = Math.max(0, config.getInt("parcels.expire-days", 7));
        this.returnToSender = config.getBoolean("parcels.return-to-sender", true);
        this.allowRecall = config.getBoolean("parcels.allow-recall", true);
        this.allowSelf = config.getBoolean("parcels.allow-self", false);
        this.cooldownSeconds = Math.max(0, config.getInt("parcels.cooldown-seconds", 10));
        this.maxPerDay = Math.max(0, config.getInt("parcels.max-per-day", 30));
        this.maxPendingPerRecipient = Math.max(0, config.getInt("parcels.max-pending-per-recipient", 5));
        this.mailboxCapacity = Math.max(0, config.getInt("parcels.mailbox-capacity", 54));
        this.noteMaxLength = Math.max(0, config.getInt("parcels.note-max-length", 120));
        this.blacklist = materials(config.getStringList("parcels.blacklist"));
        this.postageEnabled = config.getBoolean("parcels.postage.enabled", false);
        this.postageFlat = Math.max(0, config.getDouble("parcels.postage.flat", 0));
        this.postagePerStack = Math.max(0, config.getDouble("parcels.postage.per-stack", 0));

        this.codEnabled = config.getBoolean("cash-on-delivery.enabled", true);
        this.codMin = Math.max(0.01, config.getDouble("cash-on-delivery.min-price", 1));
        this.codMax = Math.max(codMin, config.getDouble("cash-on-delivery.max-price", 1_000_000_000));
        this.codTaxPercent = Math.max(0, Math.min(100, config.getDouble("cash-on-delivery.tax-percent", 0)));

        this.dropOverflow = config.getBoolean("claims.drop-overflow", false);

        this.joinSummary = config.getBoolean("notifications.join-summary", true);
        this.joinDelaySeconds = Math.max(0, config.getInt("notifications.join-delay-seconds", 2));
        this.liveNotices = config.getBoolean("notifications.live", true);
        this.notifyChat = config.getBoolean("notifications.chat", true);
        this.notifyTitle = config.getBoolean("notifications.title", true);
        this.notifyActionBar = config.getBoolean("notifications.action-bar", true);
        this.notifySound = config.getBoolean("notifications.sound", true);
        this.notifySoundKey = config.getString("notifications.sound-key", "minecraft:entity.experience_orb.pickup");

        this.announceCampaigns = config.getBoolean("campaigns.announce", true);
        this.campaignPermission = config.getString("campaigns.permission", "");

        this.codesEnabled = config.getBoolean("codes.enabled", true);
        this.codeAttemptCooldown = Math.max(0, config.getInt("codes.attempt-cooldown-seconds", 3));

        this.votesEnabled = config.getBoolean("votes.enabled", false);
        this.voteMessage = config.getString("votes.message", "");
        this.voteMoney = Math.max(0, config.getDouble("votes.money", 0));
        this.voteItems = config.getStringList("votes.items");
        this.voteCommands = config.getStringList("votes.commands");
        this.voteVoucher = config.getString("votes.voucher", "");

        this.discordEnabled = config.getBoolean("discord.enabled", false);
        this.discordUrl = config.getString("discord.webhook-url", "");
        this.discordUsername = config.getString("discord.username", "Server Mail");
        this.discordAvatar = config.getString("discord.avatar-url", "");
        this.discordColor = config.getString("discord.color", "#F5A623");

        this.mailboxBlocks = config.getBoolean("mailbox-blocks.enabled", true);
        this.mailboxLocations = new ArrayList<>(config.getStringList("mailbox-blocks.locations"));

        this.audit = config.getBoolean("audit.enabled", true);
        this.placeholderApi = config.getBoolean("placeholderapi.enabled", true);
    }

    private static Set<Material> materials(List<String> names) {
        Set<Material> out = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (material != null) out.add(material);
        }
        return out;
    }

    public StorageType storageType() {
        return storageType;
    }

    public String mysqlUrl() {
        return mysqlUrl;
    }

    public String mysqlUser() {
        return mysqlUser;
    }

    public String mysqlPassword() {
        return mysqlPassword;
    }

    public int mysqlPoolSize() {
        return mysqlPoolSize;
    }

    public ConfigurationSection sounds() {
        return raw;
    }

    public long serverMailExpiry(long now) {
        return mailExpireDays <= 0 ? 0 : now + mailExpireDays * 86_400_000L;
    }

    public long parcelExpiry(long now) {
        return parcelExpireDays <= 0 ? 0 : now + parcelExpireDays * 86_400_000L;
    }
}
