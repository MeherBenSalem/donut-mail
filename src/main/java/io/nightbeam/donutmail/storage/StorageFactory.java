package io.nightbeam.donutmail.storage;

import io.nightbeam.donutmail.config.DonutMailConfig;
import io.nightbeam.donutmail.scheduler.SchedulerAdapter;
import org.bukkit.plugin.java.JavaPlugin;

public final class StorageFactory {
    private StorageFactory() {
    }

    public static MailStorage create(JavaPlugin plugin, SchedulerAdapter scheduler, DonutMailConfig config, StorageType type) {
        return switch (type) {
            case YAML -> new YamlMailStorage(plugin, scheduler);
            case SQLITE -> new JdbcMailStorage(plugin, StorageType.SQLITE, config);
            case MYSQL -> new JdbcMailStorage(plugin, StorageType.MYSQL, config);
        };
    }
}
