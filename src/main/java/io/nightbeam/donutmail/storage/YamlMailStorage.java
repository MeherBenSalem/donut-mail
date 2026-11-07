package io.nightbeam.donutmail.storage;

import io.nightbeam.donutmail.model.BlockEntry;
import io.nightbeam.donutmail.model.Campaign;
import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.MailState;
import io.nightbeam.donutmail.model.PlayerRecord;
import io.nightbeam.donutmail.model.RewardCode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * YAML files in {@code plugins/DonutMail/data/}: {@code mails.yml} (same layout as 1.x, so 1.x data is read
 * as is), {@code campaigns.yml}, {@code players.yml} and {@code codes.yml}. Every change is written to a
 * temporary file and moved into place before the caller continues. One server only; fine for small
 * servers, use SQLite above a few thousand mails.
 */
public final class YamlMailStorage extends MemoryMailStorage {
    private final File folder;
    private final Logger logger;
    private final Function<Object, String> legacyItemEncoder;

    /**
     * @param folder            the data folder ({@code plugins/DonutMail/data})
     * @param legacyItemEncoder turns an item stored by 1.x (a Bukkit ItemStack object) into the stored string form;
     *                          returns null when it cannot
     */
    public YamlMailStorage(File folder, Logger logger, Function<Object, String> legacyItemEncoder) {
        this.folder = folder;
        this.logger = logger;
        this.legacyItemEncoder = legacyItemEncoder;
    }

    @Override
    public StorageType type() {
        return StorageType.YAML;
    }

    @Override
    public synchronized void init() throws Exception {
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Cannot create " + folder);
        }
        loadMails();
        loadCampaigns();
        loadPlayers();
        loadCodes();
    }

    private File file(String name) {
        return new File(folder, name);
    }

    @Override
    protected void persist(Section section) {
        switch (section) {
            case MAILS -> write(file("mails.yml"), saveMails());
            case CAMPAIGNS -> write(file("campaigns.yml"), saveCampaigns());
            case PLAYERS -> write(file("players.yml"), savePlayers());
            case CODES -> write(file("codes.yml"), saveCodes());
        }
    }

    private void write(File target, YamlConfiguration yaml) {
        Path path = target.toPath();
        Path temp = path.resolveSibling(target.getName() + ".tmp");
        try {
            Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new StorageException("Could not write " + target, exception);
        }
    }

    // ── Mails (1.x layout plus new keys) ────────────────────────────────────

    private void loadMails() {
        File file = file("mails.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("mails");
        if (section == null) {
            return;
        }
        int legacy = 0;
        for (String key : section.getKeys(false)) {
            ConfigurationSection root = section.getConfigurationSection(key);
            if (root == null) {
                continue;
            }
            try {
                List<String> items = new ArrayList<>();
                List<?> raw = root.getList("attachments");
                if (raw != null) {
                    for (Object value : raw) {
                        if (value instanceof String text) {
                            items.add(text);
                        } else if (value != null) {
                            String encoded = legacyItemEncoder == null ? null : legacyItemEncoder.apply(value);
                            if (encoded == null) {
                                logger.warning("Mail " + key + ": an item could not be converted and was skipped.");
                            } else {
                                items.add(encoded);
                                legacy++;
                            }
                        }
                    }
                }
                long createdAt = root.getLong("createdAt");
                MailState state = MailState.parse(root.getString("status"), MailState.PENDING);
                MailRecord mail = MailRecord.builder()
                        .id(UUID.fromString(key))
                        .kind(MailKind.parse(root.getString("kind"), MailKind.DELIVERY))
                        .receiver(UUID.fromString(root.getString("receiver")))
                        .receiverName(root.getString("receiverName"))
                        .senderId(uuid(root.getString("senderUuid")))
                        .senderName(root.getString("sender", "Console"))
                        .message(root.getString("message", ""))
                        .items(items)
                        .money(root.getDouble("money", 0))
                        .voucherId(root.getString("voucherId"))
                        .commands(root.getStringList("consoleCommands"))
                        .codPrice(root.getDouble("codPrice", 0))
                        .codPaidAt(root.getLong("codPaidAt", 0))
                        .state(state)
                        .createdAt(createdAt)
                        .expiresAt(root.getLong("expiresAt"))
                        .finishedAt(root.getLong("claimedAt", 0))
                        .readAt(root.getLong("readAt", state.isActive() ? 0 : root.getLong("claimedAt", 0)))
                        .updatedAt(root.getLong("updatedAt", Math.max(createdAt, root.getLong("claimedAt", 0))))
                        .claimToken(root.getString("claimToken"))
                        .claimServer(root.getString("claimServer"))
                        .claimAction(root.getString("claimAction"))
                        .claimAt(root.getLong("claimAt", 0))
                        .reason(root.getString("reason"))
                        .refId(uuid(root.getString("refId")))
                        .campaignId(uuid(root.getString("campaignId")))
                        .build();
                mails.put(mail.id(), mail);
            } catch (RuntimeException exception) {
                logger.warning("Skipping invalid mail entry " + key + ": " + exception.getMessage());
            }
        }
        if (legacy > 0) {
            logger.info("Converted " + legacy + " item(s) stored by DonutMail 1.x to the versioned item format.");
            persist(Section.MAILS);
        }
    }

    private YamlConfiguration saveMails() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection section = yaml.createSection("mails");
        for (MailRecord mail : mails.values()) {
            ConfigurationSection root = section.createSection(mail.id().toString());
            // 1.x keys first.
            root.set("receiver", mail.receiver().toString());
            root.set("sender", mail.senderName());
            root.set("message", mail.message());
            root.set("attachments", mail.items());
            root.set("voucherId", mail.voucherId());
            root.set("consoleCommands", mail.commands());
            root.set("createdAt", mail.createdAt());
            root.set("expiresAt", mail.expiresAt());
            root.set("status", mail.state().name());
            root.set("claimedAt", mail.finishedAt());
            // 2.x keys.
            root.set("kind", mail.kind().name());
            set(root, "receiverName", mail.receiverName());
            set(root, "senderUuid", mail.senderId());
            if (mail.money() != 0) root.set("money", mail.money());
            if (mail.codPrice() != 0) root.set("codPrice", mail.codPrice());
            if (mail.codPaidAt() != 0) root.set("codPaidAt", mail.codPaidAt());
            if (mail.readAt() != 0) root.set("readAt", mail.readAt());
            root.set("updatedAt", mail.updatedAt());
            set(root, "claimToken", mail.claimToken());
            set(root, "claimServer", mail.claimServer());
            set(root, "claimAction", mail.claimAction());
            if (mail.claimAt() != 0) root.set("claimAt", mail.claimAt());
            set(root, "reason", mail.reason());
            set(root, "refId", mail.refId());
            set(root, "campaignId", mail.campaignId());
        }
        return yaml;
    }

    // ── Campaigns ───────────────────────────────────────────────────────────

    private void loadCampaigns() {
        File file = file("campaigns.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("campaigns");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection c = section.getConfigurationSection(key);
                if (c == null) {
                    continue;
                }
                try {
                    Campaign campaign = new Campaign(UUID.fromString(key), c.getString("name"), c.getString("sender"),
                            c.getString("message"), c.getStringList("items"), c.getDouble("money"), c.getString("voucher"),
                            c.getStringList("commands"), c.getLong("startsAt"), c.getLong("endsAt"), c.getLong("createdAt"),
                            c.getString("createdBy"), c.getLong("endedAt"), c.getString("permission"),
                            c.getBoolean("existingOnly"));
                    campaigns.put(campaign.id(), campaign);
                } catch (RuntimeException exception) {
                    logger.warning("Skipping invalid campaign " + key + ": " + exception.getMessage());
                }
            }
        }
        ConfigurationSection claims = yaml.getConfigurationSection("claims");
        if (claims != null) {
            for (String key : claims.getKeys(false)) {
                Set<UUID> set = new HashSet<>();
                for (String player : claims.getStringList(key)) {
                    UUID id = uuid(player);
                    if (id != null) set.add(id);
                }
                UUID campaignId = uuid(key);
                if (campaignId != null) campaignClaims.put(campaignId, set);
            }
        }
    }

    private YamlConfiguration saveCampaigns() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection section = yaml.createSection("campaigns");
        for (Campaign campaign : campaigns.values()) {
            ConfigurationSection c = section.createSection(campaign.id().toString());
            c.set("name", campaign.name());
            c.set("sender", campaign.senderName());
            c.set("message", campaign.message());
            c.set("items", campaign.items());
            c.set("money", campaign.money());
            c.set("voucher", campaign.voucherId());
            c.set("commands", campaign.commands());
            c.set("startsAt", campaign.startsAt());
            c.set("endsAt", campaign.endsAt());
            c.set("createdAt", campaign.createdAt());
            c.set("createdBy", campaign.createdBy());
            c.set("endedAt", campaign.endedAt());
            c.set("permission", campaign.permission());
            c.set("existingOnly", campaign.existingOnly());
        }
        ConfigurationSection claims = yaml.createSection("claims");
        campaignClaims.forEach((campaign, players) -> {
            List<String> list = new ArrayList<>();
            players.forEach(p -> list.add(p.toString()));
            claims.set(campaign.toString(), list);
        });
        return yaml;
    }

    // ── Players and block lists ─────────────────────────────────────────────

    private void loadPlayers() {
        File file = file("players.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection p = section.getConfigurationSection(key);
                UUID id = uuid(key);
                if (p == null || id == null) {
                    continue;
                }
                players.put(id, new PlayerRecord(id, p.getString("name"), p.getLong("firstSeen"), p.getLong("lastSeen"),
                        p.getBoolean("acceptParcels", true), p.getLong("dailyDay", -1), p.getInt("dailyStreak"),
                        p.getInt("dailyTotal")));
            }
        }
        ConfigurationSection blockSection = yaml.getConfigurationSection("blocks");
        if (blockSection != null) {
            for (String owner : blockSection.getKeys(false)) {
                ConfigurationSection list = blockSection.getConfigurationSection(owner);
                UUID ownerId = uuid(owner);
                if (list == null || ownerId == null) {
                    continue;
                }
                Map<UUID, BlockEntry> map = new LinkedHashMap<>();
                for (String blocked : list.getKeys(false)) {
                    UUID blockedId = uuid(blocked);
                    if (blockedId != null) {
                        map.put(blockedId, new BlockEntry(ownerId, blockedId, list.getString(blocked + ".name"),
                                list.getLong(blocked + ".at")));
                    }
                }
                blocks.put(ownerId, map);
            }
        }
    }

    private YamlConfiguration savePlayers() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection section = yaml.createSection("players");
        for (PlayerRecord player : players.values()) {
            ConfigurationSection p = section.createSection(player.id().toString());
            p.set("name", player.name());
            p.set("firstSeen", player.firstSeen());
            p.set("lastSeen", player.lastSeen());
            p.set("acceptParcels", player.acceptParcels());
            p.set("dailyDay", player.dailyDay());
            p.set("dailyStreak", player.dailyStreak());
            p.set("dailyTotal", player.dailyTotal());
        }
        ConfigurationSection blockSection = yaml.createSection("blocks");
        blocks.forEach((owner, map) -> {
            for (BlockEntry entry : map.values()) {
                blockSection.set(owner + "." + entry.blocked() + ".name", entry.blockedName());
                blockSection.set(owner + "." + entry.blocked() + ".at", entry.createdAt());
            }
        });
        return yaml;
    }

    // ── Codes ───────────────────────────────────────────────────────────────

    private void loadCodes() {
        File file = file("codes.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("codes");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection c = section.getConfigurationSection(key);
                if (c == null) {
                    continue;
                }
                RewardCode code = new RewardCode(c.getString("code", key), c.getString("message"), c.getStringList("items"),
                        c.getDouble("money"), c.getString("voucher"), c.getStringList("commands"), c.getInt("maxUses"),
                        c.getInt("uses"), c.getInt("perPlayer", 1), c.getLong("expiresAt"), c.getLong("createdAt"),
                        c.getString("createdBy"), c.getBoolean("enabled", true));
                codes.put(code.code(), code);
            }
        }
        ConfigurationSection usesSection = yaml.getConfigurationSection("uses");
        if (usesSection != null) {
            for (String key : usesSection.getKeys(false)) {
                ConfigurationSection perPlayer = usesSection.getConfigurationSection(key);
                if (perPlayer == null) {
                    continue;
                }
                Map<UUID, Integer> map = new java.util.HashMap<>();
                for (String player : perPlayer.getKeys(false)) {
                    UUID id = uuid(player);
                    if (id != null) map.put(id, perPlayer.getInt(player));
                }
                codeUses.put(RewardCode.normalize(key), map);
            }
        }
    }

    private YamlConfiguration saveCodes() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection section = yaml.createSection("codes");
        for (RewardCode code : codes.values()) {
            ConfigurationSection c = section.createSection(code.code());
            c.set("code", code.code());
            c.set("message", code.message());
            c.set("items", code.items());
            c.set("money", code.money());
            c.set("voucher", code.voucherId());
            c.set("commands", code.commands());
            c.set("maxUses", code.maxUses());
            c.set("uses", code.uses());
            c.set("perPlayer", code.perPlayer());
            c.set("expiresAt", code.expiresAt());
            c.set("createdAt", code.createdAt());
            c.set("createdBy", code.createdBy());
            c.set("enabled", code.enabled());
        }
        ConfigurationSection usesSection = yaml.createSection("uses");
        codeUses.forEach((code, map) -> map.forEach((player, count) -> usesSection.set(code + "." + player, count)));
        return yaml;
    }

    private static void set(ConfigurationSection section, String key, Object value) {
        if (value != null) {
            section.set(key, value instanceof UUID ? value.toString() : value);
        }
    }

    private static UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
