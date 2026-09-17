package io.nightbeam.donutmail.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.Level;

/**
 * Copies leftover PostMaster plugin data into the DonutMail data folder when upgrading.
 */
public final class LegacyMigrator {
    private LegacyMigrator() {
    }

    public static void migrateIfNeeded(JavaPlugin plugin) {
        Path donutFolder = plugin.getDataFolder().toPath();
        Path pluginsRoot = donutFolder.getParent();
        if (pluginsRoot == null) {
            return;
        }
        Path postMasterFolder = pluginsRoot.resolve("PostMaster");
        if (!Files.isDirectory(postMasterFolder)) {
            migrateLocalDbNames(plugin);
            return;
        }

        try {
            if (!Files.exists(donutFolder)) {
                Files.createDirectories(donutFolder);
            }
            if (isEffectivelyEmpty(donutFolder)) {
                plugin.getLogger().info("Migrating PostMaster data into DonutMail...");
                copyDirectory(postMasterFolder, donutFolder);
                plugin.getLogger().info("PostMaster data migration complete.");
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to migrate PostMaster data folder", ex);
        }

        migrateLocalDbNames(plugin);
    }

    private static void migrateLocalDbNames(JavaPlugin plugin) {
        StoragePaths.ensureDataDirectory(plugin);
        Path dataDir = StoragePaths.dataDirectory(plugin).toPath();
        Path legacyRoot = plugin.getDataFolder().toPath().resolve("postmaster.db");
        Path legacyData = dataDir.resolve("postmaster.db");
        Path current = StoragePaths.sqliteFile(plugin).toPath();
        try {
            if (Files.exists(legacyRoot) && !Files.exists(current)) {
                Files.move(legacyRoot, current, StandardCopyOption.REPLACE_EXISTING);
            } else if (Files.exists(legacyData) && !Files.exists(current)) {
                Files.move(legacyData, current, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to rename legacy SQLite database", ex);
        }
    }

    private static boolean isEffectivelyEmpty(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return true;
        }
        try (var stream = Files.list(folder)) {
            return stream.findAny().isEmpty();
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Path dest = target.resolve(relative.toString());
                Files.createDirectories(dest);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                Path dest = target.resolve(relative.toString());
                Files.createDirectories(dest.getParent());
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
