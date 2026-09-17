package io.nightbeam.donutmail.config;

import io.nightbeam.donutmail.storage.StorageType;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class DonutMailConfig {
    private final StorageType storageType;
    private final String mysqlHost;
    private final int mysqlPort;
    private final String mysqlDatabase;
    private final String mysqlUsername;
    private final String mysqlPassword;
    private final int mysqlPoolSize;
    private final int expireDays;
    private final int pageSize;
    private final boolean notifyChat;
    private final boolean notifyTitle;
    private final boolean notifySound;
    private final String titleMain;
    private final String titleSub;
    private final String chatFormat;
    private final Sound sound;

    private DonutMailConfig(
            StorageType storageType,
            String mysqlHost,
            int mysqlPort,
            String mysqlDatabase,
            String mysqlUsername,
            String mysqlPassword,
            int mysqlPoolSize,
            int expireDays,
            int pageSize,
            boolean notifyChat,
            boolean notifyTitle,
            boolean notifySound,
            String titleMain,
            String titleSub,
            String chatFormat,
            Sound sound
    ) {
        this.storageType = storageType;
        this.mysqlHost = mysqlHost;
        this.mysqlPort = mysqlPort;
        this.mysqlDatabase = mysqlDatabase;
        this.mysqlUsername = mysqlUsername;
        this.mysqlPassword = mysqlPassword;
        this.mysqlPoolSize = mysqlPoolSize;
        this.expireDays = expireDays;
        this.pageSize = pageSize;
        this.notifyChat = notifyChat;
        this.notifyTitle = notifyTitle;
        this.notifySound = notifySound;
        this.titleMain = titleMain;
        this.titleSub = titleSub;
        this.chatFormat = chatFormat;
        this.sound = sound;
    }

    public static DonutMailConfig load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        StorageType type = StorageType.fromString(config.getString("storage.type", "YAML"));
        String soundKey = config.getString("notifications.sound-key", "ENTITY_EXPERIENCE_ORB_PICKUP");

        Sound resolved;
        try {
            resolved = Sound.valueOf(soundKey);
        } catch (IllegalArgumentException ex) {
            resolved = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        }

        return new DonutMailConfig(
                type,
                config.getString("storage.mysql.host", "localhost"),
                config.getInt("storage.mysql.port", 3306),
                config.getString("storage.mysql.database", "donutmail"),
                config.getString("storage.mysql.username", "root"),
                config.getString("storage.mysql.password", ""),
                config.getInt("storage.mysql.pool-size", 10),
                config.getInt("mail.expire-days", 30),
                Math.max(9, Math.min(45, config.getInt("mail.page-size", 45))),
                config.getBoolean("notifications.chat", true),
                config.getBoolean("notifications.title", true),
                config.getBoolean("notifications.sound", true),
                config.getString("notifications.title-main", "&6New Mail"),
                config.getString("notifications.title-sub", "&fUse /mail to claim it"),
                config.getString("notifications.chat-format", "&6[DonutMail] &fYou have new mail. Use &e/mail"),
                resolved
        );
    }

    public StorageType storageType() {
        return storageType;
    }

    public String mysqlHost() {
        return mysqlHost;
    }

    public int mysqlPort() {
        return mysqlPort;
    }

    public String mysqlDatabase() {
        return mysqlDatabase;
    }

    public String mysqlUsername() {
        return mysqlUsername;
    }

    public String mysqlPassword() {
        return mysqlPassword;
    }

    public int mysqlPoolSize() {
        return mysqlPoolSize;
    }

    public int expireDays() {
        return expireDays;
    }

    public int pageSize() {
        return pageSize;
    }

    public boolean notifyChat() {
        return notifyChat;
    }

    public boolean notifyTitle() {
        return notifyTitle;
    }

    public boolean notifySound() {
        return notifySound;
    }

    public String titleMain() {
        return titleMain;
    }

    public String titleSub() {
        return titleSub;
    }

    public String chatFormat() {
        return chatFormat;
    }

    public Sound sound() {
        return sound;
    }
}
