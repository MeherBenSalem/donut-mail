package io.nightbeam.donutmail.storage;

import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.MailState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteMailStorageTest extends MailStorageContract {
    @TempDir
    Path folder;

    @Override
    protected MailStorage create() {
        return new SqlMailStorage(StorageType.SQLITE, "jdbc:sqlite:" + folder.resolve("donutmail.db"), null, null, 1, SERVER,
                Logger.getLogger("test"));
    }

    @Test
    void upgradesADonutMail1Database() throws Exception {
        Path file = folder.resolve("legacy.db");
        UUID id = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file); Statement statement = connection.createStatement()) {
            // Exactly the table PostMaster created; DonutMail 1.1 renamed it to donutmail_mail.
            statement.execute("CREATE TABLE postmaster_mail (id VARCHAR(36) PRIMARY KEY, receiver_uuid VARCHAR(36) NOT NULL, "
                    + "sender VARCHAR(64) NOT NULL, message TEXT NOT NULL, attachments LONGTEXT, voucher_id VARCHAR(120), "
                    + "console_commands LONGTEXT, created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL, status VARCHAR(16) NOT NULL, "
                    + "claimed_at BIGINT NOT NULL)");
            statement.execute("INSERT INTO postmaster_mail VALUES ('" + id + "', '" + receiver + "', 'Console', '&6Hello', "
                    + "'nbt1:AAA;nbt1:BBB', 'vote_reward', 'say a\u0001say b', 1700000000000, 0, 'PENDING', 0)");
            statement.execute("INSERT INTO postmaster_mail VALUES ('" + UUID.randomUUID() + "', '" + receiver + "', 'Console', 'old', "
                    + "'', NULL, '', 1600000000000, 0, 'CLAIMED', 1600000005000)");
        }
        SqlMailStorage upgraded = new SqlMailStorage(StorageType.SQLITE, "jdbc:sqlite:" + file, null, null, 1, SERVER, Logger.getLogger("test"));
        upgraded.init();
        MailRecord mail = upgraded.mail(id).orElseThrow();
        assertEquals(List.of("nbt1:AAA", "nbt1:BBB"), mail.items());
        assertEquals(List.of("say a", "say b"), mail.commands());
        assertEquals(MailKind.DELIVERY, mail.kind());
        assertEquals(MailState.PENDING, mail.state());
        assertEquals(1700000000000L, mail.updatedAt());
        assertEquals(0, mail.readAt());
        assertEquals(1, upgraded.active(receiver).size());
        assertEquals(1, upgraded.history(receiver, 10).size());
        assertTrue(upgraded.history(receiver, 10).get(0).readAt() > 0);
        upgraded.close();
        // Opening it again changes nothing.
        SqlMailStorage again = new SqlMailStorage(StorageType.SQLITE, "jdbc:sqlite:" + file, null, null, 1, SERVER, Logger.getLogger("test"));
        again.init();
        assertEquals(List.of("nbt1:AAA", "nbt1:BBB"), again.mail(id).orElseThrow().items());
        again.close();
    }
}
