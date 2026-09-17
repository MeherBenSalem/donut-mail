package io.nightbeam.donutmail.storage;

import io.nightbeam.donutmail.model.MailEntry;
import io.nightbeam.donutmail.model.MailStatus;
import io.nightbeam.donutmail.scheduler.SchedulerAdapter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class YamlMailStorage implements MailStorage {
    private final JavaPlugin plugin;
    private final SchedulerAdapter scheduler;
    private final File file;
    private YamlConfiguration yaml;

    public YamlMailStorage(JavaPlugin plugin, SchedulerAdapter scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.file = StoragePaths.yamlMailFile(plugin);
    }

    @Override
    public StorageType getType() {
        return StorageType.YAML;
    }

    @Override
    public CompletableFuture<Void> init() {
        return CompletableFuture.runAsync(() -> {
            StoragePaths.ensureDataDirectory(plugin);
            StoragePaths.moveLegacyFileIfPresent(plugin, new File(plugin.getDataFolder(), "mails.yml"), file);
            if (!file.exists()) {
                try {
                    if (!file.createNewFile()) {
                        plugin.getLogger().warning("Failed to create mails.yml");
                    }
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
            this.yaml = YamlConfiguration.loadConfiguration(file);
        });
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<MailEntry>> loadAll() {
        return CompletableFuture.supplyAsync(() -> {
            List<MailEntry> list = new ArrayList<>();
            ConfigurationSection section = yaml.getConfigurationSection("mails");
            if (section == null) {
                return list;
            }
            for (String key : section.getKeys(false)) {
                ConfigurationSection root = section.getConfigurationSection(key);
                if (root == null) {
                    continue;
                }
                try {
                    UUID id = UUID.fromString(key);
                    UUID receiver = UUID.fromString(root.getString("receiver"));
                    String sender = root.getString("sender", "Console");
                    String message = root.getString("message", "");
                    List<ItemStack> attachments = new ArrayList<>();
                    List<?> rawAttachments = root.getList("attachments");
                    if (rawAttachments != null) {
                        for (Object obj : rawAttachments) {
                            if (obj instanceof ItemStack stack) {
                                attachments.add(stack);
                            }
                        }
                    }
                    String voucherId = root.getString("voucherId");
                    List<String> commands = root.getStringList("consoleCommands");
                    long createdAt = root.getLong("createdAt");
                    long expiresAt = root.getLong("expiresAt");
                    MailStatus status = MailStatus.valueOf(root.getString("status", "PENDING"));
                    long claimedAt = root.getLong("claimedAt", 0L);
                    list.add(new MailEntry(
                            id,
                            receiver,
                            sender,
                            message,
                            attachments,
                            voucherId,
                            commands,
                            createdAt,
                            expiresAt,
                            status,
                            claimedAt
                    ));
                } catch (Exception ex) {
                    plugin.getLogger().warning("Skipping invalid mail entry " + key + ": " + ex.getMessage());
                }
            }
            return list;
        });
    }

    @Override
    public CompletableFuture<Void> upsert(MailEntry mail) {
        return CompletableFuture.runAsync(() -> {
            ConfigurationSection mails = yaml.getConfigurationSection("mails");
            if (mails == null) {
                mails = yaml.createSection("mails");
            }
            mails.set(mail.getId().toString(), null);
            ConfigurationSection root = mails.createSection(mail.getId().toString());
            root.set("receiver", mail.getReceiverUuid().toString());
            root.set("sender", mail.getSender());
            root.set("message", mail.getMessage());
            root.set("attachments", mail.getAttachments());
            root.set("voucherId", mail.getVoucherId());
            root.set("consoleCommands", mail.getConsoleCommands());
            root.set("createdAt", mail.getCreatedAt());
            root.set("expiresAt", mail.getExpiresAt());
            root.set("status", mail.getStatus().name());
            root.set("claimedAt", mail.getClaimedAt());
            save();
        });
    }

    @Override
    public CompletableFuture<Void> upsertAll(Collection<MailEntry> mails) {
        return CompletableFuture.runAsync(() -> {
            yaml.set("mails", null);
            ConfigurationSection section = yaml.createSection("mails");
            for (MailEntry mail : mails) {
                ConfigurationSection root = section.createSection(mail.getId().toString());
                root.set("receiver", mail.getReceiverUuid().toString());
                root.set("sender", mail.getSender());
                root.set("message", mail.getMessage());
                root.set("attachments", mail.getAttachments());
                root.set("voucherId", mail.getVoucherId());
                root.set("consoleCommands", mail.getConsoleCommands());
                root.set("createdAt", mail.getCreatedAt());
                root.set("expiresAt", mail.getExpiresAt());
                root.set("status", mail.getStatus().name());
                root.set("claimedAt", mail.getClaimedAt());
            }
            save();
        });
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
