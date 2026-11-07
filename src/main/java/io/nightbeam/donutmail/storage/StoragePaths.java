package io.nightbeam.donutmail.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class StoragePaths {
    private StoragePaths() {
    }

    public static File dataDirectory(JavaPlugin plugin) {
        return dataDirectory(plugin.getDataFolder());
    }

    public static File dataDirectory(File pluginFolder) {
        return new File(pluginFolder, "data");
    }

    public static File yamlMailFile(JavaPlugin plugin) {
        return new File(dataDirectory(plugin), "mails.yml");
    }

    public static File sqliteFile(JavaPlugin plugin) {
        return new File(dataDirectory(plugin), "donutmail.db");
    }

    public static void ensureDataDirectory(JavaPlugin plugin) {
        File dir = dataDirectory(plugin);
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Failed to create storage data directory: " + dir.getAbsolutePath());
        }
    }

    public static void moveLegacyFileIfPresent(JavaPlugin plugin, File legacy, File current) {
        if (!legacy.exists() || current.exists()) {
            return;
        }
        ensureDataDirectory(plugin);
        if (!legacy.renameTo(current)) {
            plugin.getLogger().warning("Failed to move legacy storage file from " + legacy.getAbsolutePath() + " to " + current.getAbsolutePath());
        }
    }
}
