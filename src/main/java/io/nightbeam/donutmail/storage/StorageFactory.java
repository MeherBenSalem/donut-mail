package io.nightbeam.donutmail.storage;

import io.nightbeam.donutmail.config.Settings;
import io.nightbeam.donutmail.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class StorageFactory {
    private StorageFactory() {
    }

    public static MailStorage create(JavaPlugin plugin, Settings settings, String serverId) {
        StorageType type = settings.storageType();
        return switch (type) {
            case YAML -> {
                StoragePaths.ensureDataDirectory(plugin);
                StoragePaths.moveLegacyFileIfPresent(plugin, new File(plugin.getDataFolder(), "mails.yml"), StoragePaths.yamlMailFile(plugin));
                yield new YamlMailStorage(StoragePaths.dataDirectory(plugin), plugin.getLogger(), value ->
                        value instanceof ItemStack stack ? ItemSerializer.serialize(stack) : null);
            }
            case SQLITE -> {
                StoragePaths.ensureDataDirectory(plugin);
                File dbFile = StoragePaths.sqliteFile(plugin);
                StoragePaths.moveLegacyFileIfPresent(plugin, new File(plugin.getDataFolder(), "postmaster.db"), dbFile);
                StoragePaths.moveLegacyFileIfPresent(plugin, new File(StoragePaths.dataDirectory(plugin), "postmaster.db"), dbFile);
                StoragePaths.moveLegacyFileIfPresent(plugin, new File(plugin.getDataFolder(), "donutmail.db"), dbFile);
                yield new SqlMailStorage(StorageType.SQLITE, "jdbc:sqlite:" + dbFile.getAbsolutePath(), null, null, 1, serverId,
                        plugin.getLogger());
            }
            case MYSQL -> new SqlMailStorage(StorageType.MYSQL, settings.mysqlUrl(), settings.mysqlUser(), settings.mysqlPassword(),
                    settings.mysqlPoolSize(), serverId, plugin.getLogger());
        };
    }
}
