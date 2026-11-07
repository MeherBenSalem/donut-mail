package io.nightbeam.donutmail.storage;

import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.NetworkEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MySQL or MariaDB, run with -Ddonutmail.test.mysql.url=jdbc:mysql://localhost:3306/donutmail_test
 * (-Ddonutmail.test.mysql.user / .password). Every race here runs on two separate connection pools,
 * like two servers sharing one database.
 */
class MysqlMailStorageTest extends MailStorageContract {
    private static String url;
    private static String user;
    private static String password;
    private SqlMailStorage secondServer;

    @BeforeAll
    static void configured() throws Exception {
        url = System.getProperty("donutmail.test.mysql.url", "");
        user = System.getProperty("donutmail.test.mysql.user", "root");
        password = System.getProperty("donutmail.test.mysql.password", "");
        Assumptions.assumeTrue(url != null && url.startsWith("jdbc:mysql"), "set -Ddonutmail.test.mysql.url to run MySQL tests");
    }

    @Override
    protected MailStorage create() throws Exception {
        try (Connection connection = DriverManager.getConnection(url, user, password); Statement statement = connection.createStatement()) {
            for (String table : List.of("donutmail_mail", "donutmail_campaigns", "donutmail_campaign_claims", "donutmail_players",
                    "donutmail_blocks", "donutmail_codes", "donutmail_code_uses", "donutmail_events", "donutmail_meta", "postmaster_mail")) {
                statement.execute("DROP TABLE IF EXISTS " + table);
            }
        }
        return new SqlMailStorage(StorageType.MYSQL, url, user, password, 6, "server-a", Logger.getLogger("test"));
    }

    @Override
    protected MailStorage second() throws Exception {
        if (secondServer == null) {
            secondServer = new SqlMailStorage(StorageType.MYSQL, url, user, password, 6, "server-b", Logger.getLogger("test"));
            secondServer.init();
        }
        return secondServer;
    }

    @AfterEach
    void closeSecond() {
        if (secondServer != null) secondServer.close();
        secondServer = null;
    }

    @Test
    void upgradesADonutMail1Table() throws Exception {
        storage.close();
        java.util.UUID id = java.util.UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(url, user, password); Statement statement = connection.createStatement()) {
            for (String table : List.of("donutmail_mail", "donutmail_meta", "postmaster_mail")) statement.execute("DROP TABLE IF EXISTS " + table);
            statement.execute("CREATE TABLE postmaster_mail (id VARCHAR(36) PRIMARY KEY, receiver_uuid VARCHAR(36) NOT NULL, "
                    + "sender VARCHAR(64) NOT NULL, message TEXT NOT NULL, attachments LONGTEXT, voucher_id VARCHAR(120), "
                    + "console_commands LONGTEXT, created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL, status VARCHAR(16) NOT NULL, "
                    + "claimed_at BIGINT NOT NULL)");
            statement.execute("INSERT INTO postmaster_mail VALUES ('" + id + "', '" + bob + "', 'Console', 'hi', 'nbt1:AAA;nbt1:BBB', "
                    + "NULL, '', 1700000000000, 0, 'PENDING', 0)");
        }
        storage = new SqlMailStorage(StorageType.MYSQL, url, user, password, 4, "server-a", Logger.getLogger("test"));
        storage.init();
        MailRecord mail = storage.mail(id).orElseThrow();
        assertEquals(List.of("nbt1:AAA", "nbt1:BBB"), mail.items());
        assertEquals(MailKind.DELIVERY, mail.kind());
        assertEquals(1700000000000L, mail.updatedAt());
        assertEquals(1, storage.active(bob).size());
    }

    @Test
    void otherServersSeeNewMail() throws Exception {
        MailStorage other = second();
        long start = other.latestEventId();
        MailRecord mail = MailRecord.builder().kind(MailKind.DELIVERY).receiver(bob).build();
        storage.insert(mail);
        List<NetworkEvent> events = other.eventsAfter(start, 10);
        assertEquals(1, events.size());
        assertEquals(NetworkEvent.NEW_MAIL, events.get(0).type());
        assertEquals(bob, events.get(0).player());
        assertEquals(mail.id(), events.get(0).mailId());
        assertEquals("server-a", events.get(0).origin());
        storage.pruneEvents(System.currentTimeMillis() + 1000);
        assertTrue(other.eventsAfter(0, 10).isEmpty());
    }
}
