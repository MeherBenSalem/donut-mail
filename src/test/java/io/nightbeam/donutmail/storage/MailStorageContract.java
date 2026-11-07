package io.nightbeam.donutmail.storage;

import io.nightbeam.donutmail.model.BlockEntry;
import io.nightbeam.donutmail.model.Campaign;
import io.nightbeam.donutmail.model.ClaimLock;
import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.MailState;
import io.nightbeam.donutmail.model.PlayerRecord;
import io.nightbeam.donutmail.model.RedeemOutcome;
import io.nightbeam.donutmail.model.RewardCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every storage must pass these: each state change is a conditional update with exactly one winner, and
 * changes that create a second mail are all-or-nothing.
 */
abstract class MailStorageContract {
    protected MailStorage storage;

    protected final UUID alice = UUID.randomUUID();
    protected final UUID bob = UUID.randomUUID();
    protected static final String SERVER = "test-server";

    protected abstract MailStorage create() throws Exception;

    /** A second view of the same data (another server on MySQL; the same instance otherwise). */
    protected MailStorage second() throws Exception {
        return storage;
    }

    @BeforeEach
    void open() throws Exception {
        storage = create();
        storage.init();
    }

    @AfterEach
    void closeStorage() {
        storage.close();
    }

    protected MailRecord parcel(UUID from, UUID to, double cod) {
        long now = System.currentTimeMillis();
        return MailRecord.builder().kind(MailKind.PARCEL).receiver(to).receiverName("Bob").senderId(from).senderName("Alice")
                .message("hi <red>there").items(List.of("nbt1:AAAA", "nbt1:BBBB", "nbt1:CCCC")).codPrice(cod)
                .state(cod > 0 ? MailState.COD : MailState.PENDING).createdAt(now).updatedAt(now).expiresAt(now + 86_400_000L).build();
    }

    protected MailRecord returnOf(MailRecord mail) {
        return MailRecord.builder().kind(MailKind.RETURN).receiver(mail.senderId()).senderId(mail.receiver()).senderName("Bob")
                .items(mail.items()).refId(mail.id()).build();
    }

    protected MailRecord receiptOf(MailRecord mail) {
        return MailRecord.builder().kind(MailKind.RECEIPT).receiver(mail.senderId()).senderName("Bob").money(mail.codPrice())
                .refId(mail.id()).build();
    }

    // ── Basics ──────────────────────────────────────────────────────────────

    @Test
    void insertAndReadBack() {
        MailRecord mail = parcel(alice, bob, 0);
        storage.insert(mail);
        MailRecord read = storage.mail(mail.id()).orElseThrow();
        assertEquals(mail.items(), read.items());
        assertEquals(MailKind.PARCEL, read.kind());
        assertEquals(alice, read.senderId());
        assertEquals("hi <red>there", read.message());
        assertEquals(1, storage.active(bob).size());
        assertEquals(1, storage.sent(alice, 10).size());
        assertEquals(0, storage.active(alice).size());
        assertEquals(1, storage.countActive(bob, EnumSet.of(MailKind.PARCEL)));
        assertEquals(1, storage.countActiveFrom(alice, bob));
        assertEquals(1, storage.countSentSince(alice, 0));
        assertTrue(storage.stats().get("active") >= 1);
    }

    @Test
    void commandsAndMoneySurvive() {
        MailRecord mail = MailRecord.builder().kind(MailKind.DELIVERY).receiver(bob).senderName("Console").money(12.5)
                .commands(List.of("give %player% diamond 1", "say hi")).voucherId("vote_reward").build();
        storage.insert(mail);
        MailRecord read = storage.mail(mail.id()).orElseThrow();
        assertEquals(List.of("give %player% diamond 1", "say hi"), read.commands());
        assertEquals(12.5, read.money(), 0.0001);
        assertEquals("vote_reward", read.voucherId());
    }

    // ── Claims ──────────────────────────────────────────────────────────────

    @Test
    void lockOnlyFromExpectedStateAndReceiver() {
        MailRecord mail = parcel(alice, bob, 0);
        storage.insert(mail);
        ClaimLock lock = ClaimLock.create(SERVER, ClaimLock.CLAIM, System.currentTimeMillis());
        assertFalse(storage.lock(mail.id(), alice, MailState.PENDING, lock), "wrong receiver");
        assertFalse(storage.lock(mail.id(), bob, MailState.COD, lock), "wrong state");
        assertTrue(storage.lock(mail.id(), bob, MailState.PENDING, lock));
        assertFalse(storage.lock(mail.id(), bob, MailState.PENDING, ClaimLock.create(SERVER, ClaimLock.CLAIM, 1)), "already locked");
        MailRecord locked = storage.mail(mail.id()).orElseThrow();
        assertEquals(MailState.CLAIMING, locked.state());
        assertEquals(lock.token(), locked.claimToken());
        assertEquals(SERVER, locked.claimServer());
        assertEquals(1, storage.claiming(SERVER).size());
    }

    @Test
    void releaseNeedsTheToken() {
        MailRecord mail = parcel(alice, bob, 0);
        storage.insert(mail);
        ClaimLock lock = ClaimLock.create(SERVER, ClaimLock.CLAIM, System.currentTimeMillis());
        assertTrue(storage.lock(mail.id(), bob, MailState.PENDING, lock));
        assertFalse(storage.release(mail.id(), "not-the-token", MailState.PENDING, 5));
        assertTrue(storage.release(mail.id(), lock.token(), MailState.PENDING, 5));
        MailRecord back = storage.mail(mail.id()).orElseThrow();
        assertEquals(MailState.PENDING, back.state());
        assertNull(back.claimToken());
        assertFalse(storage.release(mail.id(), lock.token(), MailState.PENDING, 6), "only once");
    }

    @Test
    void fullClaimClosesTheMailAndClearsRewards() {
        MailRecord mail = MailRecord.builder().kind(MailKind.DELIVERY).receiver(bob).items(List.of("nbt1:A")).money(5)
                .commands(List.of("say x")).voucherId("v").build();
        storage.insert(mail);
        ClaimLock lock = ClaimLock.create(SERVER, ClaimLock.CLAIM, 1);
        assertTrue(storage.lock(mail.id(), bob, MailState.PENDING, lock));
        assertTrue(storage.finishClaim(mail.id(), lock.token(), List.of(), 100));
        MailRecord done = storage.mail(mail.id()).orElseThrow();
        assertEquals(MailState.CLAIMED, done.state());
        assertEquals(0, done.money(), 0.0);
        assertTrue(done.commands().isEmpty());
        assertNull(done.voucherId());
        assertTrue(done.readAt() > 0);
        assertTrue(storage.active(bob).isEmpty());
        assertEquals(1, storage.history(bob, 10).size());
        assertFalse(storage.finishClaim(mail.id(), lock.token(), List.of(), 101), "a claim finishes once");
    }

    @Test
    void partialClaimKeepsTheRest() {
        MailRecord mail = parcel(alice, bob, 0);
        storage.insert(mail);
        ClaimLock lock = ClaimLock.create(SERVER, ClaimLock.CLAIM, 1);
        assertTrue(storage.lock(mail.id(), bob, MailState.PENDING, lock));
        assertTrue(storage.finishClaim(mail.id(), lock.token(), List.of("nbt1:CCCC"), 100));
        MailRecord rest = storage.mail(mail.id()).orElseThrow();
        assertEquals(MailState.PENDING, rest.state());
        assertEquals(List.of("nbt1:CCCC"), rest.items());
        assertNull(rest.claimToken());
    }

    // ── Cash on delivery ────────────────────────────────────────────────────

    @Test
    void paymentUnlocksTheParcelAndSavesTheReceipt() {
        MailRecord mail = parcel(alice, bob, 500);
        storage.insert(mail);
        ClaimLock pay = ClaimLock.create(SERVER, ClaimLock.PAY, 1);
        assertTrue(storage.lock(mail.id(), bob, MailState.COD, pay));
        MailRecord receipt = receiptOf(mail);
        assertFalse(storage.completePayment(mail.id(), "wrong", receipt, 50));
        assertTrue(storage.mail(receipt.id()).isEmpty(), "no receipt without the payment");
        assertTrue(storage.completePayment(mail.id(), pay.token(), receipt, 50));
        MailRecord paid = storage.mail(mail.id()).orElseThrow();
        assertEquals(MailState.PENDING, paid.state());
        assertEquals(50, paid.codPaidAt());
        assertEquals(0, paid.expiresAt(), "a paid parcel never expires");
        MailRecord savedReceipt = storage.mail(receipt.id()).orElseThrow();
        assertEquals(500, savedReceipt.money(), 0.0001);
        assertEquals(alice, savedReceipt.receiver());
        assertFalse(storage.returnToSender(mail.id(), EnumSet.of(MailState.PENDING, MailState.COD), "DECLINED", returnOf(mail), null,
                null, false, 60), "a paid parcel is never returned");
    }

    @Test
    void declineReturnsTheItems() {
        MailRecord mail = parcel(alice, bob, 500);
        storage.insert(mail);
        MailRecord back = returnOf(mail);
        assertFalse(storage.returnToSender(mail.id(), EnumSet.of(MailState.COD), "DECLINED", back, alice, null, false, 10),
                "only the recipient declines");
        assertTrue(storage.returnToSender(mail.id(), EnumSet.of(MailState.COD), "DECLINED", back, bob, null, false, 10));
        assertEquals(MailState.RETURNED, storage.mail(mail.id()).orElseThrow().state());
        assertEquals("DECLINED", storage.mail(mail.id()).orElseThrow().reason());
        MailRecord returned = storage.mail(back.id()).orElseThrow();
        assertEquals(mail.items(), returned.items());
        assertEquals(alice, returned.receiver());
        assertFalse(storage.returnToSender(mail.id(), EnumSet.of(MailState.COD), "DECLINED", returnOf(mail), bob, null, false, 11),
                "declined once");
    }

    @Test
    void recallNeedsTheSender() {
        MailRecord mail = parcel(alice, bob, 0);
        storage.insert(mail);
        assertFalse(storage.returnToSender(mail.id(), EnumSet.of(MailState.PENDING), "RECALLED", returnOf(mail), null, bob, false, 1));
        assertTrue(storage.returnToSender(mail.id(), EnumSet.of(MailState.PENDING), "RECALLED", returnOf(mail), null, alice, false, 1));
    }

    @Test
    void expiryOnlyWhenDue() {
        long now = System.currentTimeMillis();
        MailRecord fresh = parcel(alice, bob, 0);
        MailRecord old = parcel(alice, bob, 200).toBuilder().id(UUID.randomUUID()).expiresAt(now - 1000).build();
        MailRecord server = MailRecord.builder().kind(MailKind.DELIVERY).receiver(bob).expiresAt(now - 1000).build();
        storage.insertAll(List.of(fresh, old, server));
        List<MailRecord> due = storage.due(now, 50);
        assertEquals(2, due.size());
        assertFalse(storage.expire(fresh.id(), now));
        assertFalse(storage.returnToSender(fresh.id(), EnumSet.of(MailState.PENDING), "EXPIRED", returnOf(fresh), null, null, true, now));
        assertTrue(storage.returnToSender(old.id(), EnumSet.of(MailState.COD), "EXPIRED", returnOf(old), null, null, true, now));
        assertTrue(storage.expire(server.id(), now));
        assertFalse(storage.expire(server.id(), now));
        assertEquals(MailState.EXPIRED, storage.mail(server.id()).orElseThrow().state());
    }

    @Test
    void staffRemoval() {
        MailRecord mail = MailRecord.builder().kind(MailKind.DELIVERY).receiver(bob).build();
        storage.insert(mail);
        assertTrue(storage.remove(mail.id(), EnumSet.of(MailState.PENDING, MailState.COD), "REMOVED", 5));
        assertEquals(MailState.DELETED, storage.mail(mail.id()).orElseThrow().state());
        assertFalse(storage.remove(mail.id(), EnumSet.of(MailState.PENDING, MailState.COD), "REMOVED", 6));
    }

    @Test
    void readMarkAndPurge() {
        MailRecord mail = MailRecord.builder().kind(MailKind.MESSAGE).receiver(bob).build();
        storage.insert(mail);
        assertTrue(storage.markRead(mail.id(), 5));
        assertFalse(storage.markRead(mail.id(), 6));
        ClaimLock lock = ClaimLock.create(SERVER, ClaimLock.CLAIM, 1);
        assertTrue(storage.lock(mail.id(), bob, MailState.PENDING, lock));
        assertTrue(storage.finishClaim(mail.id(), lock.token(), List.of(), 10));
        assertEquals(0, storage.purge(5));
        assertEquals(1, storage.purge(System.currentTimeMillis() + 1000));
        assertTrue(storage.mail(mail.id()).isEmpty());
    }

    // ── Races ───────────────────────────────────────────────────────────────

    /** Runs the tasks at the same moment and returns how many returned true. */
    protected int race(List<Callable<Boolean>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<Boolean> task : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        int wins = 0;
        for (Future<Boolean> future : futures) {
            if (future.get(60, TimeUnit.SECONDS)) wins++;
        }
        pool.shutdownNow();
        return wins;
    }

    @Test
    void twoBuyersOneParcel() throws Exception {
        for (int round = 0; round < 10; round++) {
            MailRecord mail = parcel(alice, bob, 100);
            storage.insert(mail);
            MailStorage other = second();
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                MailStorage target = i % 2 == 0 ? storage : other;
                tasks.add(() -> target.lock(mail.id(), bob, MailState.COD, ClaimLock.create("s" + Thread.currentThread().getId(),
                        ClaimLock.PAY, System.currentTimeMillis())));
            }
            assertEquals(1, race(tasks), "exactly one payment may start");
        }
    }

    @Test
    void payDeclineAndExpireRace() throws Exception {
        for (int round = 0; round < 10; round++) {
            long now = System.currentTimeMillis();
            MailRecord mail = parcel(alice, bob, 100).toBuilder().id(UUID.randomUUID()).expiresAt(now - 1).build();
            storage.insert(mail);
            MailStorage other = second();
            AtomicInteger declines = new AtomicInteger();
            AtomicInteger expiries = new AtomicInteger();
            List<MailRecord> returns = new ArrayList<>();
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                MailRecord back = returnOf(mail);
                returns.add(back);
                MailStorage target = i % 2 == 0 ? storage : other;
                tasks.add(() -> target.lock(mail.id(), bob, MailState.COD, ClaimLock.create(SERVER, ClaimLock.PAY, now)));
                tasks.add(() -> {
                    boolean ok = target.returnToSender(mail.id(), EnumSet.of(MailState.COD), "DECLINED", back, bob, null, false, now);
                    if (ok) declines.incrementAndGet();
                    return ok;
                });
                MailRecord expiredBack = returnOf(mail);
                returns.add(expiredBack);
                tasks.add(() -> {
                    boolean ok = target.returnToSender(mail.id(), EnumSet.of(MailState.PENDING, MailState.COD), "EXPIRED", expiredBack, null,
                            null, true, System.currentTimeMillis());
                    if (ok) expiries.incrementAndGet();
                    return ok;
                });
            }
            assertEquals(1, race(tasks), "pay, decline and expiry: one winner");
            long returnMails = returns.stream().filter(r -> storage.mail(r.id()).isPresent()).count();
            assertEquals(declines.get() + expiries.get(), returnMails, "a return mail exists only for the winner");
        }
    }

    @Test
    void claimAgainstRecallRace() throws Exception {
        for (int round = 0; round < 10; round++) {
            MailRecord mail = parcel(alice, bob, 0);
            storage.insert(mail);
            MailStorage other = second();
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                MailStorage target = i % 2 == 0 ? storage : other;
                tasks.add(() -> target.lock(mail.id(), bob, MailState.PENDING, ClaimLock.create(SERVER, ClaimLock.CLAIM, 1)));
                tasks.add(() -> target.returnToSender(mail.id(), EnumSet.of(MailState.PENDING), "RECALLED", returnOf(mail), null, alice, false, 1));
            }
            assertEquals(1, race(tasks));
        }
    }

    // ── Campaigns ───────────────────────────────────────────────────────────

    protected Campaign campaign(String name, long start, long end) {
        return new Campaign(UUID.randomUUID(), name, "Server", "<gold>Hello", List.of("nbt1:X"), 10, null, List.of("say hi"), start, end,
                System.currentTimeMillis(), "test", 0, null, false);
    }

    protected MailRecord copyOf(Campaign campaign, UUID player) {
        return MailRecord.builder().kind(MailKind.CAMPAIGN).receiver(player).campaignId(campaign.id()).items(campaign.items()).build();
    }

    @Test
    void campaignClaimedOncePerPlayer() throws Exception {
        Campaign campaign = campaign("summer", 0, 0);
        assertTrue(storage.insertCampaign(campaign));
        assertFalse(storage.insertCampaign(campaign("SUMMER", 0, 0)), "names are unique");
        MailStorage other = second();
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            MailStorage target = i % 2 == 0 ? storage : other;
            tasks.add(() -> target.claimCampaign(campaign.id(), bob, copyOf(campaign, bob), System.currentTimeMillis()));
        }
        assertEquals(1, race(tasks));
        assertEquals(1, storage.active(bob).size(), "one copy");
        assertTrue(storage.claimCampaign(campaign.id(), alice, copyOf(campaign, alice), System.currentTimeMillis()));
        assertEquals(2, storage.campaignClaims(campaign.id()));
        assertEquals(Set.of(campaign.id()), storage.claimedCampaigns(bob));
    }

    @Test
    void campaignWindowAndEnd() {
        long now = System.currentTimeMillis();
        Campaign later = campaign("later", now + 60_000, 0);
        Campaign over = campaign("over", 0, now - 1);
        Campaign live = campaign("live", 0, now + 60_000);
        assertTrue(storage.insertCampaign(later));
        assertTrue(storage.insertCampaign(over));
        assertTrue(storage.insertCampaign(live));
        assertFalse(storage.claimCampaign(later.id(), bob, copyOf(later, bob), now));
        assertFalse(storage.claimCampaign(over.id(), bob, copyOf(over, bob), now));
        assertTrue(storage.endCampaign(live.id(), now));
        assertFalse(storage.endCampaign(live.id(), now));
        assertFalse(storage.claimCampaign(live.id(), bob, copyOf(live, bob), now));
        assertTrue(storage.updateCampaignCommands(later.id(), List.of("a", "b")));
        assertEquals(List.of("a", "b"), storage.campaign("later").orElseThrow().commands());
        assertTrue(storage.deleteCampaign(over.id()));
        assertEquals(2, storage.campaigns().size());
    }

    // ── Players, daily rewards, block lists ─────────────────────────────────

    @Test
    void dailyRewardOncePerDay() throws Exception {
        storage.touchPlayer(bob, "Bob", 1);
        MailStorage other = second();
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            MailStorage target = i % 2 == 0 ? storage : other;
            tasks.add(() -> target.deliverDaily(bob, -1, 20_000, 1, MailRecord.builder().kind(MailKind.DAILY).receiver(bob).build()));
        }
        assertEquals(1, race(tasks));
        PlayerRecord record = storage.player(bob).orElseThrow();
        assertEquals(20_000, record.dailyDay());
        assertEquals(1, record.dailyStreak());
        assertEquals(1, record.dailyTotal());
        assertEquals(1, storage.active(bob).size());
        assertFalse(storage.deliverDaily(bob, -1, 20_001, 2, MailRecord.builder().kind(MailKind.DAILY).receiver(bob).build()));
        assertTrue(storage.deliverDaily(bob, 20_000, 20_001, 2, MailRecord.builder().kind(MailKind.DAILY).receiver(bob).build()));
        assertEquals(2, storage.player(bob).orElseThrow().dailyStreak());
    }

    @Test
    void playersBlocksAndToggle() {
        PlayerRecord first = storage.touchPlayer(bob, "Bob", 100);
        assertEquals(100, first.firstSeen());
        assertTrue(first.acceptParcels());
        storage.touchPlayer(bob, "Bobby", 200);
        assertEquals("Bobby", storage.player(bob).orElseThrow().name());
        assertEquals(100, storage.player(bob).orElseThrow().firstSeen());
        assertEquals(bob, storage.playerByName("bobby").orElseThrow().id());
        assertTrue(storage.knownNames(10).contains("Bobby"));
        storage.setAcceptParcels(bob, false);
        assertFalse(storage.player(bob).orElseThrow().acceptParcels());
        storage.setAcceptParcels(alice, false);
        assertFalse(storage.player(alice).orElseThrow().acceptParcels());
        assertTrue(storage.block(new BlockEntry(bob, alice, "Alice", 1)));
        assertFalse(storage.block(new BlockEntry(bob, alice, "Alice", 2)));
        assertTrue(storage.isBlocked(bob, alice));
        assertFalse(storage.isBlocked(alice, bob));
        assertEquals(1, storage.blocks(bob).size());
        assertTrue(storage.unblock(bob, alice));
        assertFalse(storage.unblock(bob, alice));
        assertFalse(storage.isBlocked(bob, alice));
    }

    // ── Reward codes ────────────────────────────────────────────────────────

    protected RewardCode code(String code, int max, int perPlayer, long expires) {
        return new RewardCode(code, "", List.of("nbt1:Q"), 5, null, List.of(), max, 0, perPlayer, expires, 1, "test", true);
    }

    protected MailRecord reward(UUID player) {
        return MailRecord.builder().kind(MailKind.CODE).receiver(player).build();
    }

    @Test
    void codeLimits() {
        assertTrue(storage.insertCode(code("welcome", 2, 1, 0)));
        assertFalse(storage.insertCode(code("WELCOME", 2, 1, 0)), "codes are unique, any case");
        assertEquals(RedeemOutcome.OK, storage.redeem("Welcome", bob, reward(bob), 10));
        assertEquals(RedeemOutcome.ALREADY_REDEEMED, storage.redeem("welcome", bob, reward(bob), 11));
        assertEquals(RedeemOutcome.OK, storage.redeem("welcome", alice, reward(alice), 12));
        assertEquals(RedeemOutcome.USED_UP, storage.redeem("welcome", UUID.randomUUID(), reward(bob), 13));
        assertEquals(2, storage.code("welcome").orElseThrow().uses());
        assertEquals(1, storage.codeUses("welcome", bob));
        assertEquals(RedeemOutcome.UNKNOWN, storage.redeem("nope", bob, reward(bob), 14));
        assertTrue(storage.insertCode(code("old", 0, 1, 50)));
        assertEquals(RedeemOutcome.EXPIRED, storage.redeem("old", bob, reward(bob), 60));
        assertTrue(storage.insertCode(code("many", 0, 3, 0)));
        for (int i = 0; i < 3; i++) assertEquals(RedeemOutcome.OK, storage.redeem("many", bob, reward(bob), 70 + i));
        assertEquals(RedeemOutcome.ALREADY_REDEEMED, storage.redeem("many", bob, reward(bob), 80));
        assertTrue(storage.updateCodeCommands("many", List.of("say x")));
        assertEquals(List.of("say x"), storage.code("many").orElseThrow().commands());
        assertTrue(storage.deleteCode("many"));
        assertEquals(RedeemOutcome.UNKNOWN, storage.redeem("many", alice, reward(alice), 90));
        long rewards = storage.active(bob).size();
        assertEquals(4, rewards, "one reward per successful redeem");
    }

    @Test
    void codeTotalUsesUnderRace() throws Exception {
        assertTrue(storage.insertCode(code("rush", 3, 1, 0)));
        MailStorage other = second();
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID player = UUID.randomUUID();
            MailStorage target = i % 2 == 0 ? storage : other;
            tasks.add(() -> target.redeem("rush", player, reward(player), System.currentTimeMillis()) == RedeemOutcome.OK);
        }
        assertEquals(3, race(tasks));
        assertEquals(3, storage.code("rush").orElseThrow().uses());
    }

    @Test
    void codePerPlayerUnderRace() throws Exception {
        assertTrue(storage.insertCode(code("solo", 0, 1, 0)));
        MailStorage other = second();
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            MailStorage target = i % 2 == 0 ? storage : other;
            tasks.add(() -> target.redeem("solo", bob, reward(bob), System.currentTimeMillis()) == RedeemOutcome.OK);
        }
        assertEquals(1, race(tasks));
        assertEquals(1, storage.active(bob).size());
        assertNotNull(storage.code("solo").orElse(null));
    }
}
