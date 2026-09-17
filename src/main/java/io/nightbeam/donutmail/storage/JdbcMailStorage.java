package io.nightbeam.donutmail.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.nightbeam.donutmail.config.DonutMailConfig;
import io.nightbeam.donutmail.model.MailEntry;
import io.nightbeam.donutmail.model.MailStatus;
import io.nightbeam.donutmail.util.ItemSerializer;
import io.nightbeam.donutmail.util.ListCodec;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class JdbcMailStorage implements MailStorage {
    private final JavaPlugin plugin;
    private final StorageType type;
    private final DonutMailConfig config;
    private HikariDataSource dataSource;

    public JdbcMailStorage(JavaPlugin plugin, StorageType type, DonutMailConfig config) {
        this.plugin = plugin;
        this.type = type;
        this.config = config;
    }

    @Override
    public StorageType getType() {
        return type;
    }

    @Override
    public CompletableFuture<Void> init() {
        return CompletableFuture.runAsync(() -> {
            HikariConfig hikari = new HikariConfig();
            if (type == StorageType.SQLITE) {
                StoragePaths.ensureDataDirectory(plugin);
                File dbFile = StoragePaths.sqliteFile(plugin);
                StoragePaths.moveLegacyFileIfPresent(plugin, new File(plugin.getDataFolder(), "postmaster.db"), dbFile);
                StoragePaths.moveLegacyFileIfPresent(plugin, new File(StoragePaths.dataDirectory(plugin), "postmaster.db"), dbFile);
                StoragePaths.moveLegacyFileIfPresent(plugin, new File(plugin.getDataFolder(), "donutmail.db"), dbFile);
                hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
                hikari.setMaximumPoolSize(1);
            } else {
                hikari.setJdbcUrl("jdbc:mysql://" + config.mysqlHost() + ":" + config.mysqlPort() + "/" + config.mysqlDatabase()
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
                hikari.setUsername(config.mysqlUsername());
                hikari.setPassword(config.mysqlPassword());
                hikari.setMaximumPoolSize(config.mysqlPoolSize());
            }
            hikari.setPoolName("DonutMail-" + type.name());
            this.dataSource = new HikariDataSource(hikari);
            renameLegacyTableIfPresent();
            createTableIfMissing();
        });
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            if (dataSource != null) {
                dataSource.close();
            }
        });
    }

    @Override
    public CompletableFuture<List<MailEntry>> loadAll() {
        return CompletableFuture.supplyAsync(() -> {
            List<MailEntry> list = new ArrayList<>();
            String sql = "SELECT * FROM donutmail_mail";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(new MailEntry(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("receiver_uuid")),
                            rs.getString("sender"),
                            rs.getString("message"),
                            ItemSerializer.deserializeList(rs.getString("attachments")),
                            rs.getString("voucher_id"),
                            ListCodec.decode(rs.getString("console_commands")),
                            rs.getLong("created_at"),
                            rs.getLong("expires_at"),
                            MailStatus.valueOf(rs.getString("status")),
                            rs.getLong("claimed_at")
                    ));
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            return list;
        });
    }

    @Override
    public CompletableFuture<Void> upsert(MailEntry mail) {
        return CompletableFuture.runAsync(() -> writeOne(mail));
    }

    @Override
    public CompletableFuture<Void> upsertAll(Collection<MailEntry> mails) {
        return CompletableFuture.runAsync(() -> {
            String deleteSql = "DELETE FROM donutmail_mail";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement delete = connection.prepareStatement(deleteSql)) {
                delete.executeUpdate();
                for (MailEntry mail : mails) {
                    writeOne(connection, mail);
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private void writeOne(MailEntry mail) {
        try (Connection connection = dataSource.getConnection()) {
            writeOne(connection, mail);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void writeOne(Connection connection, MailEntry mail) throws Exception {
        String sql = "INSERT INTO donutmail_mail (id, receiver_uuid, sender, message, attachments, voucher_id, console_commands, created_at, expires_at, status, claimed_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(id) DO UPDATE SET receiver_uuid=excluded.receiver_uuid, sender=excluded.sender, message=excluded.message, attachments=excluded.attachments, voucher_id=excluded.voucher_id, console_commands=excluded.console_commands, created_at=excluded.created_at, expires_at=excluded.expires_at, status=excluded.status, claimed_at=excluded.claimed_at";

        String mysqlSql = "INSERT INTO donutmail_mail (id, receiver_uuid, sender, message, attachments, voucher_id, console_commands, created_at, expires_at, status, claimed_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE receiver_uuid=VALUES(receiver_uuid), sender=VALUES(sender), message=VALUES(message), attachments=VALUES(attachments), voucher_id=VALUES(voucher_id), console_commands=VALUES(console_commands), created_at=VALUES(created_at), expires_at=VALUES(expires_at), status=VALUES(status), claimed_at=VALUES(claimed_at)";

        String statementSql = type == StorageType.MYSQL ? mysqlSql : sql;

        try (PreparedStatement statement = connection.prepareStatement(statementSql)) {
            statement.setString(1, mail.getId().toString());
            statement.setString(2, mail.getReceiverUuid().toString());
            statement.setString(3, mail.getSender());
            statement.setString(4, mail.getMessage());
            statement.setString(5, ItemSerializer.serializeList(mail.getAttachments()));
            statement.setString(6, mail.getVoucherId());
            statement.setString(7, ListCodec.encode(mail.getConsoleCommands()));
            statement.setLong(8, mail.getCreatedAt());
            statement.setLong(9, mail.getExpiresAt());
            statement.setString(10, mail.getStatus().name());
            statement.setLong(11, mail.getClaimedAt());
            statement.executeUpdate();
        }
    }

    private void createTableIfMissing() {
        String sql = "CREATE TABLE IF NOT EXISTS donutmail_mail ("
                + "id VARCHAR(36) PRIMARY KEY,"
                + "receiver_uuid VARCHAR(36) NOT NULL,"
                + "sender VARCHAR(64) NOT NULL,"
                + "message TEXT NOT NULL,"
                + "attachments LONGTEXT,"
                + "voucher_id VARCHAR(120),"
                + "console_commands LONGTEXT,"
                + "created_at BIGINT NOT NULL,"
                + "expires_at BIGINT NOT NULL,"
                + "status VARCHAR(16) NOT NULL,"
                + "claimed_at BIGINT NOT NULL"
                + ")";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void renameLegacyTableIfPresent() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            boolean hasLegacy = tableExists(connection, "postmaster_mail");
            boolean hasCurrent = tableExists(connection, "donutmail_mail");
            if (hasLegacy && !hasCurrent) {
                statement.execute("ALTER TABLE postmaster_mail RENAME TO donutmail_mail");
                plugin.getLogger().info("Renamed legacy table postmaster_mail to donutmail_mail");
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to migrate legacy mail table", ex);
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws Exception {
        try (ResultSet rs = connection.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = connection.getMetaData().getTables(null, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}
