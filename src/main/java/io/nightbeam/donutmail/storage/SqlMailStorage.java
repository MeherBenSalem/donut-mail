package io.nightbeam.donutmail.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.nightbeam.donutmail.model.BlockEntry;
import io.nightbeam.donutmail.model.Campaign;
import io.nightbeam.donutmail.model.ClaimLock;
import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.MailState;
import io.nightbeam.donutmail.model.NetworkEvent;
import io.nightbeam.donutmail.model.PlayerRecord;
import io.nightbeam.donutmail.model.RedeemOutcome;
import io.nightbeam.donutmail.model.RewardCode;
import io.nightbeam.donutmail.util.ListCodec;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * SQLite (one server) or MySQL/MariaDB (one server or several sharing the database).
 *
 * <p>Every state change is an {@code UPDATE ... WHERE state = expected} whose row count says who won. When
 * a change also creates a mail (receipt, returned parcel, campaign copy, daily reward, code reward), both
 * happen in one transaction. MySQL runs at READ COMMITTED, so a second server that tries the same update
 * waits for the first to commit, re-reads the row and finds it no longer matches.
 */
public final class SqlMailStorage implements MailStorage {
    private static final String MAIL = "donutmail_mail";
    private static final String MAIL_COLUMNS = "id, receiver_uuid, sender, message, attachments, voucher_id, console_commands, "
            + "created_at, expires_at, status, claimed_at, kind, receiver_name, sender_uuid, money, cod_price, cod_paid_at, "
            + "read_at, updated_at, claim_token, claim_server, claim_action, claim_at, reason, ref_id, campaign_id";
    private static final String ITEM_SEPARATOR = ";";

    private final StorageType type;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int poolSize;
    private final String serverId;
    private final Logger logger;
    private HikariDataSource dataSource;

    public SqlMailStorage(StorageType type, String jdbcUrl, String username, String password, int poolSize,
                          String serverId, Logger logger) {
        if (type == StorageType.YAML) {
            throw new IllegalArgumentException("YAML is not SQL");
        }
        this.type = type;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.poolSize = poolSize;
        this.serverId = serverId;
        this.logger = logger;
    }

    private boolean mysql() {
        return type == StorageType.MYSQL;
    }

    @Override
    public StorageType type() {
        return type;
    }

    @Override
    public void init() throws Exception {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(jdbcUrl);
        hikari.setPoolName("DonutMail-" + type.name());
        if (mysql()) {
            hikari.setDriverClassName(com.mysql.cj.jdbc.Driver.class.getName());
            hikari.setUsername(username);
            hikari.setPassword(password);
            hikari.setMaximumPoolSize(Math.max(2, poolSize));
            hikari.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        } else {
            hikari.setDriverClassName("org.sqlite.JDBC");
            // One connection: SQLite writes are serialized anyway, and it keeps every update strictly ordered.
            hikari.setMaximumPoolSize(1);
            hikari.setConnectionInitSql("PRAGMA busy_timeout = 10000");
        }
        hikari.setConnectionTimeout(15_000);
        this.dataSource = new HikariDataSource(hikari);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            if (!mysql()) {
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = NORMAL");
            }
            renameLegacyTable(connection);
            createTables(connection, statement);
            migrate(connection, statement);
        }
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    // ── Schema ──────────────────────────────────────────────────────────────

    private void renameLegacyTable(Connection connection) throws SQLException {
        if (tableExists(connection, "postmaster_mail") && !tableExists(connection, MAIL)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE postmaster_mail RENAME TO " + MAIL);
            }
            logger.info("Renamed legacy table postmaster_mail to " + MAIL);
        }
    }

    private String tableSuffix() {
        return mysql() ? " DEFAULT CHARSET=utf8mb4" : "";
    }

    private void createTables(Connection connection, Statement statement) throws SQLException {
        // The 1.x table, unchanged; new columns are added by migrate().
        statement.execute("CREATE TABLE IF NOT EXISTS " + MAIL + " ("
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
                + ")" + tableSuffix());
        statement.execute("CREATE TABLE IF NOT EXISTS donutmail_campaigns ("
                + "id VARCHAR(36) PRIMARY KEY,"
                + "name VARCHAR(64) NOT NULL,"
                + "name_key VARCHAR(64) NOT NULL UNIQUE,"
                + "sender VARCHAR(64) NOT NULL,"
                + "message TEXT NOT NULL,"
                + "items LONGTEXT,"
                + "money DOUBLE NOT NULL DEFAULT 0,"
                + "voucher_id VARCHAR(120),"
                + "commands LONGTEXT,"
                + "starts_at BIGINT NOT NULL DEFAULT 0,"
                + "ends_at BIGINT NOT NULL DEFAULT 0,"
                + "created_at BIGINT NOT NULL,"
                + "created_by VARCHAR(64),"
                + "ended_at BIGINT NOT NULL DEFAULT 0,"
                + "permission VARCHAR(128),"
                + "existing_only INT NOT NULL DEFAULT 0"
                + ")" + tableSuffix());
        statement.execute("CREATE TABLE IF NOT EXISTS donutmail_campaign_claims ("
                + "campaign_id VARCHAR(36) NOT NULL,"
                + "player_uuid VARCHAR(36) NOT NULL,"
                + "claimed_at BIGINT NOT NULL,"
                + "PRIMARY KEY (campaign_id, player_uuid)"
                + ")" + tableSuffix());
        statement.execute("CREATE TABLE IF NOT EXISTS donutmail_players ("
                + "uuid VARCHAR(36) PRIMARY KEY,"
                + "name VARCHAR(32),"
                + "name_key VARCHAR(32),"
                + "first_seen BIGINT NOT NULL,"
                + "last_seen BIGINT NOT NULL,"
                + "accept_parcels INT NOT NULL DEFAULT 1,"
                + "daily_day BIGINT NOT NULL DEFAULT -1,"
                + "daily_streak INT NOT NULL DEFAULT 0,"
                + "daily_total INT NOT NULL DEFAULT 0"
                + ")" + tableSuffix());
        statement.execute("CREATE TABLE IF NOT EXISTS donutmail_blocks ("
                + "owner_uuid VARCHAR(36) NOT NULL,"
                + "blocked_uuid VARCHAR(36) NOT NULL,"
                + "blocked_name VARCHAR(32),"
                + "created_at BIGINT NOT NULL,"
                + "PRIMARY KEY (owner_uuid, blocked_uuid)"
                + ")" + tableSuffix());
        statement.execute("CREATE TABLE IF NOT EXISTS donutmail_codes ("
                + "code VARCHAR(64) PRIMARY KEY,"
                + "message TEXT,"
                + "items LONGTEXT,"
                + "money DOUBLE NOT NULL DEFAULT 0,"
                + "voucher_id VARCHAR(120),"
                + "commands LONGTEXT,"
                + "max_uses INT NOT NULL DEFAULT 0,"
                + "uses INT NOT NULL DEFAULT 0,"
                + "per_player INT NOT NULL DEFAULT 1,"
                + "expires_at BIGINT NOT NULL DEFAULT 0,"
                + "created_at BIGINT NOT NULL,"
                + "created_by VARCHAR(64),"
                + "enabled INT NOT NULL DEFAULT 1"
                + ")" + tableSuffix());
        statement.execute("CREATE TABLE IF NOT EXISTS donutmail_code_uses ("
                + "code VARCHAR(64) NOT NULL,"
                + "player_uuid VARCHAR(36) NOT NULL,"
                + "uses INT NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (code, player_uuid)"
                + ")" + tableSuffix());
        statement.execute("CREATE TABLE IF NOT EXISTS donutmail_events ("
                + (mysql() ? "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," : "id INTEGER PRIMARY KEY AUTOINCREMENT,")
                + "created_at BIGINT NOT NULL,"
                + "origin VARCHAR(64) NOT NULL,"
                + "type VARCHAR(16) NOT NULL,"
                + "player_uuid VARCHAR(36),"
                + "mail_id VARCHAR(36)"
                + ")" + tableSuffix());
        statement.execute("CREATE TABLE IF NOT EXISTS donutmail_meta ("
                + "meta_key VARCHAR(64) PRIMARY KEY,"
                + "meta_value VARCHAR(255)"
                + ")" + tableSuffix());
    }

    private void migrate(Connection connection, Statement statement) throws SQLException {
        Set<String> columns = columns(connection, MAIL);
        boolean upgraded = !columns.contains("kind");
        addColumn(statement, columns, "kind", "VARCHAR(16) NOT NULL DEFAULT 'DELIVERY'");
        addColumn(statement, columns, "receiver_name", "VARCHAR(32)");
        addColumn(statement, columns, "sender_uuid", "VARCHAR(36)");
        addColumn(statement, columns, "money", "DOUBLE NOT NULL DEFAULT 0");
        addColumn(statement, columns, "cod_price", "DOUBLE NOT NULL DEFAULT 0");
        addColumn(statement, columns, "cod_paid_at", "BIGINT NOT NULL DEFAULT 0");
        addColumn(statement, columns, "read_at", "BIGINT NOT NULL DEFAULT 0");
        addColumn(statement, columns, "updated_at", "BIGINT NOT NULL DEFAULT 0");
        addColumn(statement, columns, "claim_token", "VARCHAR(36)");
        addColumn(statement, columns, "claim_server", "VARCHAR(64)");
        addColumn(statement, columns, "claim_action", "VARCHAR(16)");
        addColumn(statement, columns, "claim_at", "BIGINT NOT NULL DEFAULT 0");
        addColumn(statement, columns, "reason", "VARCHAR(32)");
        addColumn(statement, columns, "ref_id", "VARCHAR(36)");
        addColumn(statement, columns, "campaign_id", "VARCHAR(36)");
        if (upgraded) {
            int rows = statement.executeUpdate("UPDATE " + MAIL + " SET updated_at = CASE WHEN claimed_at > created_at "
                    + "THEN claimed_at ELSE created_at END, read_at = CASE WHEN status = 'PENDING' THEN 0 ELSE claimed_at END "
                    + "WHERE updated_at = 0");
            if (rows > 0) {
                logger.info("Upgraded " + rows + " mail(s) stored by DonutMail 1.x.");
            }
        }
        index(connection, statement, MAIL, "idx_dm_mail_receiver", "receiver_uuid, status");
        index(connection, statement, MAIL, "idx_dm_mail_sender", "sender_uuid, created_at");
        index(connection, statement, MAIL, "idx_dm_mail_due", "status, expires_at");
        index(connection, statement, MAIL, "idx_dm_mail_updated", "updated_at");
        index(connection, statement, "donutmail_players", "idx_dm_players_name", "name_key");
        index(connection, statement, "donutmail_events", "idx_dm_events_created", "created_at");
        try (PreparedStatement select = connection.prepareStatement("SELECT meta_value FROM donutmail_meta WHERE meta_key = 'schema'");
             ResultSet rs = select.executeQuery()) {
            if (!rs.next()) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO donutmail_meta (meta_key, meta_value) VALUES ('schema', '2')")) {
                    insert.executeUpdate();
                } catch (SQLException race) {
                    if (!isDuplicate(race)) throw race;
                }
            }
        }
    }

    private void addColumn(Statement statement, Set<String> existing, String column, String definition) throws SQLException {
        if (existing.contains(column)) {
            return;
        }
        try {
            statement.execute("ALTER TABLE " + MAIL + " ADD COLUMN " + column + " " + definition);
        } catch (SQLException exception) {
            // Another server may have added it a moment ago.
            if (!columns(statement.getConnection(), MAIL).contains(column)) {
                throw exception;
            }
        }
    }

    private Set<String> columns(Connection connection, String table) throws SQLException {
        Set<String> out = new HashSet<>();
        DatabaseMetaData meta = connection.getMetaData();
        for (String name : new String[]{table, table.toUpperCase(Locale.ROOT)}) {
            try (ResultSet rs = meta.getColumns(connection.getCatalog(), null, name, null)) {
                while (rs.next()) {
                    out.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
            if (!out.isEmpty()) break;
        }
        return out;
    }

    private void index(Connection connection, Statement statement, String table, String name, String columns) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, table, false, false)) {
            while (rs.next()) {
                String existing = rs.getString("INDEX_NAME");
                if (existing != null && existing.equalsIgnoreCase(name)) {
                    return;
                }
            }
        }
        try {
            statement.execute("CREATE INDEX " + name + " ON " + table + " (" + columns + ")");
        } catch (SQLException exception) {
            // Created by another server in the meantime (duplicate key name).
            logger.fine("Index " + name + ": " + exception.getMessage());
        }
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        for (String name : new String[]{table, table.toUpperCase(Locale.ROOT)}) {
            try (ResultSet rs = meta.getTables(connection.getCatalog(), null, name, new String[]{"TABLE"})) {
                if (rs.next()) return true;
            }
        }
        return false;
    }

    // ── Plumbing ────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    /** Ends a transaction with a rollback and returns {@code value}. */
    private static final class Rollback extends RuntimeException {
        private final transient Object value;

        Rollback(Object value) {
            super(null, null, false, false);
            this.value = value;
        }
    }

    private <T> T read(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            return work.run(connection);
        } catch (SQLException exception) {
            throw new StorageException("Database read failed: " + exception.getMessage(), exception);
        }
    }

    /** Runs {@code work} in a transaction; retried on deadlocks and lock wait timeouts (MySQL). */
    @SuppressWarnings("unchecked")
    private <T> T tx(SqlWork<T> work) {
        SQLException last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    T result = work.run(connection);
                    connection.commit();
                    return result;
                } catch (Rollback rollback) {
                    connection.rollback();
                    return (T) rollback.value;
                } catch (SQLException | RuntimeException exception) {
                    try {
                        connection.rollback();
                    } catch (SQLException ignored) {
                        // The original failure matters more.
                    }
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException exception) {
                last = exception;
                if (!retryable(exception)) {
                    break;
                }
                try {
                    Thread.sleep(25L * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new StorageException("Database write failed: " + (last == null ? "unknown" : last.getMessage()), last);
    }

    private static boolean retryable(SQLException exception) {
        String state = exception.getSQLState();
        int code = exception.getErrorCode();
        return "40001".equals(state) || code == 1213 || code == 1205
                || (exception.getMessage() != null && exception.getMessage().contains("SQLITE_BUSY"));
    }

    static boolean isDuplicate(SQLException exception) {
        String state = exception.getSQLState();
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        return exception.getErrorCode() == 1062 || (state != null && state.startsWith("23"))
                || message.contains("UNIQUE constraint failed") || message.contains("PRIMARY KEY constraint")
                || message.contains("SQLITE_CONSTRAINT");
    }

    private static int update(Connection connection, String sql, Binder binder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            return statement.executeUpdate();
        }
    }

    private List<MailRecord> mails(Connection connection, String where, Binder binder) throws SQLException {
        List<MailRecord> out = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT " + MAIL_COLUMNS + " FROM " + MAIL + " " + where)) {
            binder.bind(statement);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    MailRecord mail = readMail(rs);
                    if (mail != null) out.add(mail);
                }
            }
        }
        return out;
    }

    private MailRecord readMail(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        try {
            long createdAt = rs.getLong("created_at");
            return MailRecord.builder()
                    .id(UUID.fromString(id))
                    .kind(MailKind.parse(rs.getString("kind"), MailKind.DELIVERY))
                    .receiver(UUID.fromString(rs.getString("receiver_uuid")))
                    .receiverName(rs.getString("receiver_name"))
                    .senderId(uuid(rs.getString("sender_uuid")))
                    .senderName(rs.getString("sender"))
                    .message(rs.getString("message"))
                    .items(splitItems(rs.getString("attachments")))
                    .money(rs.getDouble("money"))
                    .voucherId(rs.getString("voucher_id"))
                    .commands(ListCodec.decode(rs.getString("console_commands")))
                    .codPrice(rs.getDouble("cod_price"))
                    .codPaidAt(rs.getLong("cod_paid_at"))
                    .state(MailState.parse(rs.getString("status"), MailState.PENDING))
                    .createdAt(createdAt)
                    .expiresAt(rs.getLong("expires_at"))
                    .finishedAt(rs.getLong("claimed_at"))
                    .readAt(rs.getLong("read_at"))
                    .updatedAt(rs.getLong("updated_at"))
                    .claimToken(rs.getString("claim_token"))
                    .claimServer(rs.getString("claim_server"))
                    .claimAction(rs.getString("claim_action"))
                    .claimAt(rs.getLong("claim_at"))
                    .reason(rs.getString("reason"))
                    .refId(uuid(rs.getString("ref_id")))
                    .campaignId(uuid(rs.getString("campaign_id")))
                    .build();
        } catch (RuntimeException invalid) {
            logger.warning("Skipping unreadable mail row " + id + ": " + invalid.getMessage());
            return null;
        }
    }

    private void insertMail(Connection connection, MailRecord mail) throws SQLException {
        String sql = "INSERT INTO " + MAIL + " (" + MAIL_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        update(connection, sql, s -> {
            s.setString(1, mail.id().toString());
            s.setString(2, mail.receiver().toString());
            s.setString(3, truncate(mail.senderName(), 64));
            s.setString(4, mail.message());
            s.setString(5, joinItems(mail.items()));
            s.setString(6, mail.voucherId());
            s.setString(7, ListCodec.encode(mail.commands()));
            s.setLong(8, mail.createdAt());
            s.setLong(9, mail.expiresAt());
            s.setString(10, mail.state().name());
            s.setLong(11, mail.finishedAt());
            s.setString(12, mail.kind().name());
            s.setString(13, truncate(mail.receiverName(), 32));
            s.setString(14, str(mail.senderId()));
            s.setDouble(15, mail.money());
            s.setDouble(16, mail.codPrice());
            s.setLong(17, mail.codPaidAt());
            s.setLong(18, mail.readAt());
            s.setLong(19, mail.updatedAt());
            s.setString(20, mail.claimToken());
            s.setString(21, mail.claimServer());
            s.setString(22, mail.claimAction());
            s.setLong(23, mail.claimAt());
            s.setString(24, mail.reason());
            s.setString(25, str(mail.refId()));
            s.setString(26, str(mail.campaignId()));
        });
        event(connection, NetworkEvent.NEW_MAIL, mail.receiver(), mail.id());
    }

    private void event(Connection connection, String type, UUID player, UUID mailId) throws SQLException {
        if (!mysql()) {
            return;
        }
        update(connection, "INSERT INTO donutmail_events (created_at, origin, type, player_uuid, mail_id) VALUES (?, ?, ?, ?, ?)", s -> {
            s.setLong(1, System.currentTimeMillis());
            s.setString(2, serverId);
            s.setString(3, type);
            s.setString(4, str(player));
            s.setString(5, str(mailId));
        });
    }

    /** Tells the receiver's server (and, for parcels, the sender's) that a mail changed. */
    private void changed(Connection connection, UUID id) throws SQLException {
        if (!mysql()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT receiver_uuid, sender_uuid FROM " + MAIL + " WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    event(connection, NetworkEvent.UPDATE, uuid(rs.getString(1)), id);
                    UUID sender = uuid(rs.getString(2));
                    if (sender != null) event(connection, NetworkEvent.UPDATE, sender, id);
                }
            }
        }
    }

    // ── Mail: reads ─────────────────────────────────────────────────────────

    @Override
    public Optional<MailRecord> mail(UUID id) {
        return read(c -> mails(c, "WHERE id = ?", s -> s.setString(1, id.toString())).stream().findFirst());
    }

    @Override
    public List<MailRecord> active(UUID receiver) {
        return read(c -> mails(c, "WHERE receiver_uuid = ? AND status IN ('PENDING', 'COD', 'CLAIMING') ORDER BY created_at DESC, id",
                s -> s.setString(1, receiver.toString())));
    }

    @Override
    public List<MailRecord> sent(UUID sender, int limit) {
        return read(c -> mails(c, "WHERE sender_uuid = ? AND kind = 'PARCEL' ORDER BY created_at DESC, id LIMIT " + Math.max(1, limit),
                s -> s.setString(1, sender.toString())));
    }

    @Override
    public List<MailRecord> history(UUID receiver, int limit) {
        return read(c -> mails(c, "WHERE receiver_uuid = ? AND status IN ('CLAIMED', 'RETURNED', 'EXPIRED', 'DELETED') "
                + "ORDER BY updated_at DESC LIMIT " + Math.max(1, limit), s -> s.setString(1, receiver.toString())));
    }

    @Override
    public List<MailRecord> due(long now, int limit) {
        return read(c -> mails(c, "WHERE status IN ('PENDING', 'COD') AND expires_at > 0 AND expires_at <= ? ORDER BY expires_at LIMIT "
                + Math.max(1, limit), s -> s.setLong(1, now)));
    }

    @Override
    public List<MailRecord> claiming(String server) {
        return read(c -> mails(c, "WHERE status = 'CLAIMING' AND claim_server = ?", s -> s.setString(1, server)));
    }

    @Override
    public int countActive(UUID receiver, Set<MailKind> kinds) {
        if (kinds.isEmpty()) return 0;
        String in = kinds.stream().map(k -> "'" + k.name() + "'").collect(Collectors.joining(", "));
        return read(c -> count(c, "SELECT COUNT(*) FROM " + MAIL + " WHERE receiver_uuid = ? AND status IN ('PENDING', 'COD', 'CLAIMING') "
                + "AND kind IN (" + in + ")", s -> s.setString(1, receiver.toString())));
    }

    @Override
    public int countActiveFrom(UUID sender, UUID receiver) {
        return read(c -> count(c, "SELECT COUNT(*) FROM " + MAIL + " WHERE receiver_uuid = ? AND sender_uuid = ? AND kind = 'PARCEL' "
                + "AND status IN ('PENDING', 'COD', 'CLAIMING')", s -> {
            s.setString(1, receiver.toString());
            s.setString(2, sender.toString());
        }));
    }

    @Override
    public int countSentSince(UUID sender, long since) {
        return read(c -> count(c, "SELECT COUNT(*) FROM " + MAIL + " WHERE sender_uuid = ? AND kind = 'PARCEL' AND created_at >= ?", s -> {
            s.setString(1, sender.toString());
            s.setLong(2, since);
        }));
    }

    private int count(Connection connection, String sql, Binder binder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    @Override
    public Map<String, Long> stats() {
        return read(c -> {
            Map<String, Long> out = new LinkedHashMap<>();
            out.put("mail", (long) count(c, "SELECT COUNT(*) FROM " + MAIL, s -> { }));
            out.put("active", (long) count(c, "SELECT COUNT(*) FROM " + MAIL + " WHERE status IN ('PENDING', 'COD', 'CLAIMING')", s -> { }));
            out.put("cod", (long) count(c, "SELECT COUNT(*) FROM " + MAIL + " WHERE status = 'COD'", s -> { }));
            out.put("claiming", (long) count(c, "SELECT COUNT(*) FROM " + MAIL + " WHERE status = 'CLAIMING'", s -> { }));
            out.put("campaigns", (long) count(c, "SELECT COUNT(*) FROM donutmail_campaigns", s -> { }));
            out.put("codes", (long) count(c, "SELECT COUNT(*) FROM donutmail_codes", s -> { }));
            out.put("players", (long) count(c, "SELECT COUNT(*) FROM donutmail_players", s -> { }));
            return out;
        });
    }

    // ── Mail: writes ────────────────────────────────────────────────────────

    @Override
    public void insert(MailRecord mail) {
        tx(c -> {
            insertMail(c, mail);
            return null;
        });
    }

    @Override
    public void insertAll(List<MailRecord> list) {
        if (list.isEmpty()) return;
        tx(c -> {
            for (MailRecord mail : list) {
                insertMail(c, mail);
            }
            return null;
        });
    }

    @Override
    public boolean lock(UUID id, UUID receiver, MailState from, ClaimLock lock) {
        return tx(c -> {
            int rows = update(c, "UPDATE " + MAIL + " SET status = 'CLAIMING', claim_token = ?, claim_server = ?, claim_action = ?, "
                    + "claim_at = ?, reason = ?, updated_at = ? WHERE id = ? AND status = ?"
                    + (receiver != null ? " AND receiver_uuid = ?" : ""), s -> {
                s.setString(1, lock.token());
                s.setString(2, lock.server());
                s.setString(3, lock.action());
                s.setLong(4, lock.at());
                s.setString(5, from.name());
                s.setLong(6, lock.at());
                s.setString(7, id.toString());
                s.setString(8, from.name());
                if (receiver != null) s.setString(9, receiver.toString());
            });
            return rows == 1;
        });
    }

    @Override
    public boolean release(UUID id, String token, MailState back, long now) {
        return tx(c -> {
            int rows = update(c, "UPDATE " + MAIL + " SET status = ?, claim_token = NULL, claim_server = NULL, claim_action = NULL, "
                    + "claim_at = 0, reason = NULL, updated_at = ? WHERE id = ? AND status = 'CLAIMING' AND claim_token = ?", s -> {
                s.setString(1, back.name());
                s.setLong(2, now);
                s.setString(3, id.toString());
                s.setString(4, token);
            });
            if (rows == 1) changed(c, id);
            return rows == 1;
        });
    }

    @Override
    public boolean finishClaim(UUID id, String token, List<String> remaining, long now) {
        boolean done = remaining == null || remaining.isEmpty();
        return tx(c -> {
            int rows = update(c, "UPDATE " + MAIL + " SET status = ?, attachments = ?, money = 0, voucher_id = NULL, console_commands = '', "
                    + "claimed_at = ?, read_at = CASE WHEN read_at = 0 THEN ? ELSE read_at END, claim_token = NULL, claim_server = NULL, "
                    + "claim_action = NULL, claim_at = 0, reason = NULL, updated_at = ? "
                    + "WHERE id = ? AND status = 'CLAIMING' AND claim_token = ?", s -> {
                s.setString(1, done ? MailState.CLAIMED.name() : MailState.PENDING.name());
                s.setString(2, done ? null : joinItems(remaining));
                s.setLong(3, done ? now : 0);
                s.setLong(4, now);
                s.setLong(5, now);
                s.setString(6, id.toString());
                s.setString(7, token);
            });
            if (rows == 1) changed(c, id);
            return rows == 1;
        });
    }

    @Override
    public boolean completePayment(UUID id, String token, MailRecord receipt, long now) {
        return tx(c -> {
            int rows = update(c, "UPDATE " + MAIL + " SET status = 'PENDING', cod_paid_at = ?, expires_at = 0, claim_token = NULL, "
                    + "claim_server = NULL, claim_action = NULL, claim_at = 0, reason = NULL, updated_at = ? "
                    + "WHERE id = ? AND status = 'CLAIMING' AND claim_token = ? AND cod_price > 0 AND cod_paid_at = 0", s -> {
                s.setLong(1, now);
                s.setLong(2, now);
                s.setString(3, id.toString());
                s.setString(4, token);
            });
            if (rows != 1) {
                throw new Rollback(false);
            }
            changed(c, id);
            insertMail(c, receipt);
            return true;
        });
    }

    @Override
    public boolean returnToSender(UUID id, Set<MailState> from, String reason, MailRecord returned, UUID requireReceiver,
                                  UUID requireSender, boolean onlyIfDue, long now) {
        if (from.isEmpty()) return false;
        String states = from.stream().map(s -> "'" + s.name() + "'").collect(Collectors.joining(", "));
        return tx(c -> {
            StringBuilder sql = new StringBuilder("UPDATE " + MAIL + " SET status = 'RETURNED', reason = ?, claimed_at = ?, updated_at = ?, "
                    + "claim_token = NULL, claim_server = NULL, claim_action = NULL, claim_at = 0 "
                    + "WHERE id = ? AND status IN (" + states + ") AND cod_paid_at = 0");
            if (requireReceiver != null) sql.append(" AND receiver_uuid = ?");
            if (requireSender != null) sql.append(" AND sender_uuid = ?");
            if (onlyIfDue) sql.append(" AND expires_at > 0 AND expires_at <= ?");
            int rows = update(c, sql.toString(), s -> {
                int i = 1;
                s.setString(i++, reason);
                s.setLong(i++, now);
                s.setLong(i++, now);
                s.setString(i++, id.toString());
                if (requireReceiver != null) s.setString(i++, requireReceiver.toString());
                if (requireSender != null) s.setString(i++, requireSender.toString());
                if (onlyIfDue) s.setLong(i, now);
            });
            if (rows != 1) {
                throw new Rollback(false);
            }
            changed(c, id);
            insertMail(c, returned);
            return true;
        });
    }

    @Override
    public boolean expire(UUID id, long now) {
        return tx(c -> {
            int rows = update(c, "UPDATE " + MAIL + " SET status = 'EXPIRED', reason = 'EXPIRED', claimed_at = ?, updated_at = ? "
                    + "WHERE id = ? AND status IN ('PENDING', 'COD') AND expires_at > 0 AND expires_at <= ?", s -> {
                s.setLong(1, now);
                s.setLong(2, now);
                s.setString(3, id.toString());
                s.setLong(4, now);
            });
            if (rows == 1) changed(c, id);
            return rows == 1;
        });
    }

    @Override
    public boolean remove(UUID id, Set<MailState> from, String reason, long now) {
        if (from.isEmpty()) return false;
        String states = from.stream().map(s -> "'" + s.name() + "'").collect(Collectors.joining(", "));
        return tx(c -> {
            int rows = update(c, "UPDATE " + MAIL + " SET status = 'DELETED', reason = ?, claimed_at = ?, updated_at = ?, claim_token = NULL, "
                    + "claim_server = NULL, claim_action = NULL, claim_at = 0 WHERE id = ? AND status IN (" + states + ")", s -> {
                s.setString(1, reason);
                s.setLong(2, now);
                s.setLong(3, now);
                s.setString(4, id.toString());
            });
            if (rows == 1) changed(c, id);
            return rows == 1;
        });
    }

    @Override
    public boolean markRead(UUID id, long now) {
        return tx(c -> update(c, "UPDATE " + MAIL + " SET read_at = ? WHERE id = ? AND read_at = 0", s -> {
            s.setLong(1, now);
            s.setString(2, id.toString());
        }) == 1);
    }

    @Override
    public int purge(long before) {
        return tx(c -> update(c, "DELETE FROM " + MAIL + " WHERE status IN ('CLAIMED', 'RETURNED', 'EXPIRED', 'DELETED') AND updated_at < ?",
                s -> s.setLong(1, before)));
    }

    // ── Campaigns ───────────────────────────────────────────────────────────

    @Override
    public boolean insertCampaign(Campaign campaign) {
        return tx(c -> {
            try {
                update(c, "INSERT INTO donutmail_campaigns (id, name, name_key, sender, message, items, money, voucher_id, commands, "
                        + "starts_at, ends_at, created_at, created_by, ended_at, permission, existing_only) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", s -> {
                    s.setString(1, campaign.id().toString());
                    s.setString(2, campaign.name());
                    s.setString(3, campaign.name().toLowerCase(Locale.ROOT));
                    s.setString(4, truncate(campaign.senderName(), 64));
                    s.setString(5, campaign.message());
                    s.setString(6, joinItems(campaign.items()));
                    s.setDouble(7, campaign.money());
                    s.setString(8, campaign.voucherId());
                    s.setString(9, ListCodec.encode(campaign.commands()));
                    s.setLong(10, campaign.startsAt());
                    s.setLong(11, campaign.endsAt());
                    s.setLong(12, campaign.createdAt());
                    s.setString(13, campaign.createdBy());
                    s.setLong(14, campaign.endedAt());
                    s.setString(15, campaign.permission());
                    s.setInt(16, campaign.existingOnly() ? 1 : 0);
                });
            } catch (SQLException exception) {
                if (isDuplicate(exception)) throw new Rollback(false);
                throw exception;
            }
            event(c, NetworkEvent.CAMPAIGN, null, null);
            return true;
        });
    }

    @Override
    public List<Campaign> campaigns() {
        return read(c -> campaignRows(c, "", s -> { }));
    }

    @Override
    public Optional<Campaign> campaign(String name) {
        return read(c -> campaignRows(c, "WHERE name_key = ?", s -> s.setString(1, name.toLowerCase(Locale.ROOT))).stream().findFirst());
    }

    private List<Campaign> campaignRows(Connection connection, String where, Binder binder) throws SQLException {
        List<Campaign> out = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM donutmail_campaigns " + where + " ORDER BY created_at")) {
            binder.bind(statement);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.add(new Campaign(UUID.fromString(rs.getString("id")), rs.getString("name"), rs.getString("sender"),
                            rs.getString("message"), splitItems(rs.getString("items")), rs.getDouble("money"),
                            rs.getString("voucher_id"), ListCodec.decode(rs.getString("commands")), rs.getLong("starts_at"),
                            rs.getLong("ends_at"), rs.getLong("created_at"), rs.getString("created_by"), rs.getLong("ended_at"),
                            rs.getString("permission"), rs.getInt("existing_only") == 1));
                }
            }
        }
        return out;
    }

    @Override
    public boolean endCampaign(UUID id, long now) {
        return tx(c -> {
            int rows = update(c, "UPDATE donutmail_campaigns SET ended_at = ? WHERE id = ? AND ended_at = 0", s -> {
                s.setLong(1, now);
                s.setString(2, id.toString());
            });
            if (rows == 1) event(c, NetworkEvent.CAMPAIGN, null, null);
            return rows == 1;
        });
    }

    @Override
    public boolean deleteCampaign(UUID id) {
        return tx(c -> {
            int rows = update(c, "DELETE FROM donutmail_campaigns WHERE id = ?", s -> s.setString(1, id.toString()));
            update(c, "DELETE FROM donutmail_campaign_claims WHERE campaign_id = ?", s -> s.setString(1, id.toString()));
            if (rows == 1) event(c, NetworkEvent.CAMPAIGN, null, null);
            return rows == 1;
        });
    }

    @Override
    public boolean updateCampaignCommands(UUID id, List<String> commands) {
        return tx(c -> {
            int rows = update(c, "UPDATE donutmail_campaigns SET commands = ? WHERE id = ?", s -> {
                s.setString(1, ListCodec.encode(commands));
                s.setString(2, id.toString());
            });
            if (rows == 1) event(c, NetworkEvent.CAMPAIGN, null, null);
            return rows == 1;
        });
    }

    @Override
    public boolean claimCampaign(UUID campaignId, UUID player, MailRecord copy, long now) {
        return tx(c -> {
            int live = count(c, "SELECT COUNT(*) FROM donutmail_campaigns WHERE id = ? AND ended_at = 0 AND (starts_at = 0 OR starts_at <= ?) "
                    + "AND (ends_at = 0 OR ends_at > ?)", s -> {
                s.setString(1, campaignId.toString());
                s.setLong(2, now);
                s.setLong(3, now);
            });
            if (live != 1) {
                throw new Rollback(false);
            }
            try {
                update(c, "INSERT INTO donutmail_campaign_claims (campaign_id, player_uuid, claimed_at) VALUES (?, ?, ?)", s -> {
                    s.setString(1, campaignId.toString());
                    s.setString(2, player.toString());
                    s.setLong(3, now);
                });
            } catch (SQLException exception) {
                if (isDuplicate(exception)) throw new Rollback(false);
                throw exception;
            }
            insertMail(c, copy);
            return true;
        });
    }

    @Override
    public Set<UUID> claimedCampaigns(UUID player) {
        return read(c -> {
            Set<UUID> out = new HashSet<>();
            try (PreparedStatement statement = c.prepareStatement("SELECT campaign_id FROM donutmail_campaign_claims WHERE player_uuid = ?")) {
                statement.setString(1, player.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        UUID id = uuid(rs.getString(1));
                        if (id != null) out.add(id);
                    }
                }
            }
            return out;
        });
    }

    @Override
    public int campaignClaims(UUID campaignId) {
        return read(c -> count(c, "SELECT COUNT(*) FROM donutmail_campaign_claims WHERE campaign_id = ?", s -> s.setString(1, campaignId.toString())));
    }

    // ── Players ─────────────────────────────────────────────────────────────

    private Optional<PlayerRecord> playerRow(Connection connection, String where, Binder binder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM donutmail_players " + where)) {
            binder.bind(statement);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new PlayerRecord(UUID.fromString(rs.getString("uuid")), rs.getString("name"), rs.getLong("first_seen"),
                        rs.getLong("last_seen"), rs.getInt("accept_parcels") == 1, rs.getLong("daily_day"), rs.getInt("daily_streak"),
                        rs.getInt("daily_total")));
            }
        }
    }

    @Override
    public Optional<PlayerRecord> player(UUID id) {
        return read(c -> playerRow(c, "WHERE uuid = ?", s -> s.setString(1, id.toString())));
    }

    @Override
    public Optional<PlayerRecord> playerByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return read(c -> playerRow(c, "WHERE name_key = ? ORDER BY last_seen DESC LIMIT 1",
                s -> s.setString(1, name.toLowerCase(Locale.ROOT))));
    }

    @Override
    public PlayerRecord touchPlayer(UUID id, String name, long now) {
        return tx(c -> {
            int rows = update(c, "UPDATE donutmail_players SET name = COALESCE(?, name), name_key = COALESCE(?, name_key), last_seen = ? WHERE uuid = ?", s -> {
                s.setString(1, name);
                s.setString(2, name == null ? null : name.toLowerCase(Locale.ROOT));
                s.setLong(3, now);
                s.setString(4, id.toString());
            });
            if (rows == 0) {
                try {
                    insertPlayer(c, id, name, now);
                } catch (SQLException exception) {
                    if (!isDuplicate(exception)) throw exception;
                }
            }
            return playerRow(c, "WHERE uuid = ?", s -> s.setString(1, id.toString())).orElse(PlayerRecord.fresh(id, name, now));
        });
    }

    private void insertPlayer(Connection connection, UUID id, String name, long now) throws SQLException {
        update(connection, "INSERT INTO donutmail_players (uuid, name, name_key, first_seen, last_seen, accept_parcels, daily_day, daily_streak, daily_total) "
                + "VALUES (?, ?, ?, ?, ?, 1, -1, 0, 0)", s -> {
            s.setString(1, id.toString());
            s.setString(2, name);
            s.setString(3, name == null ? null : name.toLowerCase(Locale.ROOT));
            s.setLong(4, now);
            s.setLong(5, now);
        });
    }

    @Override
    public List<String> knownNames(int limit) {
        return read(c -> {
            List<String> out = new ArrayList<>();
            try (PreparedStatement statement = c.prepareStatement("SELECT name FROM donutmail_players WHERE name IS NOT NULL ORDER BY last_seen DESC LIMIT " + Math.max(1, limit));
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
            return out;
        });
    }

    @Override
    public boolean deliverDaily(UUID player, long expectedDay, long newDay, int newStreak, MailRecord reward) {
        return tx(c -> {
            int rows = update(c, "UPDATE donutmail_players SET daily_day = ?, daily_streak = ?, daily_total = daily_total + 1 "
                    + "WHERE uuid = ? AND daily_day = ?", s -> {
                s.setLong(1, newDay);
                s.setInt(2, newStreak);
                s.setString(3, player.toString());
                s.setLong(4, expectedDay);
            });
            if (rows != 1) {
                throw new Rollback(false);
            }
            insertMail(c, reward);
            return true;
        });
    }

    @Override
    public void setDaily(UUID player, long day, int streak) {
        tx(c -> {
            Binder binder = s -> {
                s.setLong(1, day);
                s.setInt(2, streak);
                s.setString(3, player.toString());
            };
            if (update(c, "UPDATE donutmail_players SET daily_day = ?, daily_streak = ? WHERE uuid = ?", binder) == 0) {
                insertPlayer(c, player, null, System.currentTimeMillis());
                update(c, "UPDATE donutmail_players SET daily_day = ?, daily_streak = ? WHERE uuid = ?", binder);
            }
            return null;
        });
    }

    @Override
    public void setAcceptParcels(UUID player, boolean accept) {
        tx(c -> {
            int rows = update(c, "UPDATE donutmail_players SET accept_parcels = ? WHERE uuid = ?", s -> {
                s.setInt(1, accept ? 1 : 0);
                s.setString(2, player.toString());
            });
            if (rows == 0) {
                insertPlayer(c, player, null, System.currentTimeMillis());
                update(c, "UPDATE donutmail_players SET accept_parcels = ? WHERE uuid = ?", s -> {
                    s.setInt(1, accept ? 1 : 0);
                    s.setString(2, player.toString());
                });
            }
            return null;
        });
    }

    @Override
    public boolean block(BlockEntry entry) {
        return tx(c -> {
            try {
                update(c, "INSERT INTO donutmail_blocks (owner_uuid, blocked_uuid, blocked_name, created_at) VALUES (?, ?, ?, ?)", s -> {
                    s.setString(1, entry.owner().toString());
                    s.setString(2, entry.blocked().toString());
                    s.setString(3, truncate(entry.blockedName(), 32));
                    s.setLong(4, entry.createdAt());
                });
                return true;
            } catch (SQLException exception) {
                if (isDuplicate(exception)) throw new Rollback(false);
                throw exception;
            }
        });
    }

    @Override
    public boolean unblock(UUID owner, UUID blocked) {
        return tx(c -> update(c, "DELETE FROM donutmail_blocks WHERE owner_uuid = ? AND blocked_uuid = ?", s -> {
            s.setString(1, owner.toString());
            s.setString(2, blocked.toString());
        }) == 1);
    }

    @Override
    public List<BlockEntry> blocks(UUID owner) {
        return read(c -> {
            List<BlockEntry> out = new ArrayList<>();
            try (PreparedStatement statement = c.prepareStatement("SELECT * FROM donutmail_blocks WHERE owner_uuid = ? ORDER BY created_at")) {
                statement.setString(1, owner.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        out.add(new BlockEntry(owner, UUID.fromString(rs.getString("blocked_uuid")), rs.getString("blocked_name"),
                                rs.getLong("created_at")));
                    }
                }
            }
            return out;
        });
    }

    @Override
    public boolean isBlocked(UUID owner, UUID sender) {
        return read(c -> count(c, "SELECT COUNT(*) FROM donutmail_blocks WHERE owner_uuid = ? AND blocked_uuid = ?", s -> {
            s.setString(1, owner.toString());
            s.setString(2, sender.toString());
        }) > 0);
    }

    // ── Reward codes ────────────────────────────────────────────────────────

    @Override
    public boolean insertCode(RewardCode code) {
        return tx(c -> {
            try {
                update(c, "INSERT INTO donutmail_codes (code, message, items, money, voucher_id, commands, max_uses, uses, per_player, "
                        + "expires_at, created_at, created_by, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", s -> {
                    s.setString(1, code.code());
                    s.setString(2, code.message());
                    s.setString(3, joinItems(code.items()));
                    s.setDouble(4, code.money());
                    s.setString(5, code.voucherId());
                    s.setString(6, ListCodec.encode(code.commands()));
                    s.setInt(7, code.maxUses());
                    s.setInt(8, code.uses());
                    s.setInt(9, code.perPlayer());
                    s.setLong(10, code.expiresAt());
                    s.setLong(11, code.createdAt());
                    s.setString(12, code.createdBy());
                    s.setInt(13, code.enabled() ? 1 : 0);
                });
                return true;
            } catch (SQLException exception) {
                if (isDuplicate(exception)) throw new Rollback(false);
                throw exception;
            }
        });
    }

    private Optional<RewardCode> codeRow(Connection connection, String code) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM donutmail_codes WHERE code = ?")) {
            statement.setString(1, RewardCode.normalize(code));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(code(rs)) : Optional.empty();
            }
        }
    }

    private RewardCode code(ResultSet rs) throws SQLException {
        return new RewardCode(rs.getString("code"), rs.getString("message"), splitItems(rs.getString("items")), rs.getDouble("money"),
                rs.getString("voucher_id"), ListCodec.decode(rs.getString("commands")), rs.getInt("max_uses"), rs.getInt("uses"),
                rs.getInt("per_player"), rs.getLong("expires_at"), rs.getLong("created_at"), rs.getString("created_by"),
                rs.getInt("enabled") == 1);
    }

    @Override
    public Optional<RewardCode> code(String code) {
        return read(c -> codeRow(c, code));
    }

    @Override
    public List<RewardCode> codes() {
        return read(c -> {
            List<RewardCode> out = new ArrayList<>();
            try (PreparedStatement statement = c.prepareStatement("SELECT * FROM donutmail_codes ORDER BY created_at");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) out.add(code(rs));
            }
            return out;
        });
    }

    @Override
    public boolean deleteCode(String code) {
        String key = RewardCode.normalize(code);
        return tx(c -> {
            int rows = update(c, "DELETE FROM donutmail_codes WHERE code = ?", s -> s.setString(1, key));
            update(c, "DELETE FROM donutmail_code_uses WHERE code = ?", s -> s.setString(1, key));
            return rows == 1;
        });
    }

    @Override
    public boolean updateCodeCommands(String code, List<String> commands) {
        return tx(c -> update(c, "UPDATE donutmail_codes SET commands = ? WHERE code = ?", s -> {
            s.setString(1, ListCodec.encode(commands));
            s.setString(2, RewardCode.normalize(code));
        }) == 1);
    }

    @Override
    public int codeUses(String code, UUID player) {
        return read(c -> count(c, "SELECT COALESCE(SUM(uses), 0) FROM donutmail_code_uses WHERE code = ? AND player_uuid = ?", s -> {
            s.setString(1, RewardCode.normalize(code));
            s.setString(2, player.toString());
        }));
    }

    @Override
    public RedeemOutcome redeem(String code, UUID player, MailRecord reward, long now) {
        String key = RewardCode.normalize(code);
        return tx(c -> {
            // Count the use first: this locks the code row, so redemptions of one code run one at a time.
            int rows = update(c, "UPDATE donutmail_codes SET uses = uses + 1 WHERE code = ? AND enabled = 1 "
                    + "AND (max_uses = 0 OR uses < max_uses) AND (expires_at = 0 OR expires_at > ?)", s -> {
                s.setString(1, key);
                s.setLong(2, now);
            });
            if (rows != 1) {
                Optional<RewardCode> current = codeRow(c, key);
                RedeemOutcome why = current.isEmpty() ? RedeemOutcome.UNKNOWN
                        : !current.get().enabled() ? RedeemOutcome.DISABLED
                        : current.get().isExpired(now) ? RedeemOutcome.EXPIRED
                        : RedeemOutcome.USED_UP;
                throw new Rollback(why);
            }
            int perPlayer = count(c, "SELECT per_player FROM donutmail_codes WHERE code = ?", s -> s.setString(1, key));
            int mine = update(c, "UPDATE donutmail_code_uses SET uses = uses + 1 WHERE code = ? AND player_uuid = ?"
                    + (perPlayer > 0 ? " AND uses < ?" : ""), s -> {
                s.setString(1, key);
                s.setString(2, player.toString());
                if (perPlayer > 0) s.setInt(3, perPlayer);
            });
            if (mine != 1) {
                int existing = count(c, "SELECT COUNT(*) FROM donutmail_code_uses WHERE code = ? AND player_uuid = ?", s -> {
                    s.setString(1, key);
                    s.setString(2, player.toString());
                });
                if (existing > 0) {
                    throw new Rollback(RedeemOutcome.ALREADY_REDEEMED);
                }
                try {
                    update(c, "INSERT INTO donutmail_code_uses (code, player_uuid, uses) VALUES (?, ?, 1)", s -> {
                        s.setString(1, key);
                        s.setString(2, player.toString());
                    });
                } catch (SQLException exception) {
                    if (isDuplicate(exception)) throw new Rollback(RedeemOutcome.ALREADY_REDEEMED);
                    throw exception;
                }
            }
            insertMail(c, reward);
            return RedeemOutcome.OK;
        });
    }

    // ── Several servers ─────────────────────────────────────────────────────

    @Override
    public long latestEventId() {
        return read(c -> {
            try (PreparedStatement statement = c.prepareStatement("SELECT COALESCE(MAX(id), 0) FROM donutmail_events");
                 ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        });
    }

    @Override
    public List<NetworkEvent> eventsAfter(long id, int limit) {
        return read(c -> {
            List<NetworkEvent> out = new ArrayList<>();
            try (PreparedStatement statement = c.prepareStatement("SELECT * FROM donutmail_events WHERE id > ? ORDER BY id LIMIT " + Math.max(1, limit))) {
                statement.setLong(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        out.add(new NetworkEvent(rs.getLong("id"), rs.getLong("created_at"), rs.getString("origin"), rs.getString("type"),
                                uuid(rs.getString("player_uuid")), uuid(rs.getString("mail_id"))));
                    }
                }
            }
            return out;
        });
    }

    @Override
    public void publish(String type, UUID player, UUID mailId) {
        if (!mysql()) return;
        tx(c -> {
            event(c, type, player, mailId);
            return null;
        });
    }

    @Override
    public void pruneEvents(long before) {
        if (!mysql()) return;
        tx(c -> update(c, "DELETE FROM donutmail_events WHERE created_at < ?", s -> s.setLong(1, before)));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    static String joinItems(List<String> items) {
        return items == null || items.isEmpty() ? "" : String.join(ITEM_SEPARATOR, items);
    }

    static List<String> splitItems(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(ITEM_SEPARATOR)).filter(s -> !s.isBlank()).collect(Collectors.toList());
    }

    private static String str(UUID value) {
        return value == null ? null : value.toString();
    }

    private static UUID uuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
