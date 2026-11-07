package io.nightbeam.donutmail.storage;

import io.nightbeam.donutmail.model.Campaign;
import io.nightbeam.donutmail.model.ClaimLock;
import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.MailState;
import io.nightbeam.donutmail.model.RedeemOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlMailStorageTest extends MailStorageContract {
    @TempDir
    Path folder;

    @Override
    protected MailStorage create() {
        return new YamlMailStorage(folder.toFile(), Logger.getLogger("test"), value -> null);
    }

    @Test
    void everythingSurvivesARestart() throws Exception {
        MailRecord mail = parcel(alice, bob, 250);
        storage.insert(mail);
        ClaimLock lock = ClaimLock.create(SERVER, ClaimLock.PAY, 7);
        assertTrue(storage.lock(mail.id(), bob, MailState.COD, lock));
        Campaign campaign = campaign("event", 0, 0);
        storage.insertCampaign(campaign);
        storage.claimCampaign(campaign.id(), bob, copyOf(campaign, bob), System.currentTimeMillis());
        storage.touchPlayer(bob, "Bob", 1);
        storage.deliverDaily(bob, -1, 5, 1, MailRecord.builder().kind(MailKind.DAILY).receiver(bob).build());
        storage.insertCode(code("keep", 5, 1, 0));
        assertEquals(RedeemOutcome.OK, storage.redeem("keep", bob, reward(bob), 2));
        storage.close();

        MailStorage reopened = create();
        reopened.init();
        MailRecord locked = reopened.mail(mail.id()).orElseThrow();
        assertEquals(MailState.CLAIMING, locked.state(), "a lock survives a crash, so recovery can decide");
        assertEquals(lock.token(), locked.claimToken());
        assertEquals("COD", locked.reason(), "the state to go back to is remembered");
        assertEquals(250, locked.codPrice(), 0.0001);
        assertEquals(1, reopened.claimedCampaigns(bob).size());
        assertEquals(5, reopened.player(bob).orElseThrow().dailyDay());
        assertEquals(RedeemOutcome.ALREADY_REDEEMED, reopened.redeem("keep", bob, reward(bob), 3));
        assertEquals(1, reopened.code("keep").orElseThrow().uses());
        reopened.close();
    }

    @Test
    void readsDonutMail1Files() throws Exception {
        // The layout DonutMail 1.1.0 wrote: items are Bukkit objects (a map here) handed to the converter.
        UUID id = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        String yaml = "mails:\n"
                + "  " + id + ":\n"
                + "    receiver: " + receiver + "\n"
                + "    sender: Console\n"
                + "    message: '&6Voucher Delivery'\n"
                + "    attachments:\n"
                + "    - type: DIAMOND\n"
                + "      amount: 3\n"
                + "    voucherId: vote_reward\n"
                + "    consoleCommands:\n"
                + "    - eco give %player% 10\n"
                + "    createdAt: 1700000000000\n"
                + "    expiresAt: 0\n"
                + "    status: PENDING\n"
                + "    claimedAt: 0\n";
        Path data = Files.createDirectories(folder.resolve("legacy"));
        Files.writeString(data.resolve("mails.yml"), yaml);
        AtomicInteger converted = new AtomicInteger();
        YamlMailStorage legacy = new YamlMailStorage(data.toFile(), Logger.getLogger("test"), value -> {
            converted.incrementAndGet();
            return "nbt1:CONVERTED";
        });
        legacy.init();
        MailRecord mail = legacy.mail(id).orElseThrow();
        assertEquals(1, converted.get());
        assertEquals(List.of("nbt1:CONVERTED"), mail.items());
        assertEquals(MailKind.DELIVERY, mail.kind());
        assertEquals(MailState.PENDING, mail.state());
        assertEquals("vote_reward", mail.voucherId());
        assertEquals(List.of("eco give %player% 10"), mail.commands());
        assertEquals(1700000000000L, mail.updatedAt());
        assertEquals(1, legacy.active(receiver).size());
        String rewritten = Files.readString(new File(data.toFile(), "mails.yml").toPath());
        assertTrue(rewritten.contains("nbt1:CONVERTED"), "converted items are written back");
        assertTrue(rewritten.contains("status: PENDING"), "1.x keys are kept");
        legacy.close();
    }
}
