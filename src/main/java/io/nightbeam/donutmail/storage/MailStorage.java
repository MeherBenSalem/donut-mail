package io.nightbeam.donutmail.storage;

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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Mail storage. Methods are blocking and are always called from DonutMail's storage threads.
 *
 * <p>Every method that changes the state of a mail is a single conditional update: it checks the
 * current state and changes it in one atomic step, and returns {@code false} when the mail was not in
 * the expected state any more. Whoever gets {@code true} won; nobody else may act on that mail. With
 * MySQL this holds across servers. Methods that also create a mail (a receipt, a returned parcel, a
 * campaign copy, a daily reward) do both in one transaction.
 */
public interface MailStorage extends AutoCloseable {

    StorageType type();

    void init() throws Exception;

    @Override
    void close();

    // ── Mail: reads ─────────────────────────────────────────────────────────

    Optional<MailRecord> mail(UUID id);

    /** PENDING, COD and CLAIMING mail of a player, newest first. */
    List<MailRecord> active(UUID receiver);

    /** Parcels a player sent, newest first. */
    List<MailRecord> sent(UUID sender, int limit);

    /** Finished mail of a player, newest first. */
    List<MailRecord> history(UUID receiver, int limit);

    /** PENDING or COD mail whose expiry has passed. */
    List<MailRecord> due(long now, int limit);

    /** Mail locked by the given server (claims or payments in progress or interrupted). */
    List<MailRecord> claiming(String server);

    int countActive(UUID receiver, Set<MailKind> kinds);

    int countActiveFrom(UUID sender, UUID receiver);

    int countSentSince(UUID sender, long since);

    /** Counts for {@code /mail admin info}. */
    Map<String, Long> stats();

    // ── Mail: writes ────────────────────────────────────────────────────────

    void insert(MailRecord mail);

    /** Inserts all mails in one transaction. */
    void insertAll(List<MailRecord> mails);

    /** {@code from} → CLAIMING with the lock. {@code receiver} null skips the receiver check. */
    boolean lock(UUID id, UUID receiver, MailState from, ClaimLock lock);

    /** CLAIMING(token) → {@code back}. */
    boolean release(UUID id, String token, MailState back, long now);

    /**
     * CLAIMING(token) → CLAIMED when {@code remaining} is empty, otherwise PENDING with only the remaining
     * items. Money, voucher and commands are cleared either way, because they are handed over with the
     * first claim.
     */
    boolean finishClaim(UUID id, String token, List<String> remaining, long now);

    /**
     * CLAIMING(token) of a cash-on-delivery parcel → PENDING and paid (the items now belong to the
     * recipient, and it no longer expires), and the receipt is inserted, in one transaction.
     */
    boolean completePayment(UUID id, String token, MailRecord receipt, long now);

    /**
     * One of {@code from} → RETURNED with {@code reason}, and the returned parcel is inserted, in one
     * transaction. Optional checks: {@code requireReceiver} / {@code requireSender} must match, and with
     * {@code onlyIfDue} the mail must have expired. A paid cash-on-delivery parcel is never returned.
     */
    boolean returnToSender(UUID id, Set<MailState> from, String reason, MailRecord returned, UUID requireReceiver,
                           UUID requireSender, boolean onlyIfDue, long now);

    /** PENDING or COD and past its expiry → EXPIRED (nothing to return). */
    boolean expire(UUID id, long now);

    /** One of {@code from} → DELETED with {@code reason} (staff). */
    boolean remove(UUID id, Set<MailState> from, String reason, long now);

    boolean markRead(UUID id, long now);

    /** Deletes finished mail last changed before {@code before}. */
    int purge(long before);

    // ── Campaigns ───────────────────────────────────────────────────────────

    /** False when the name is taken. */
    boolean insertCampaign(Campaign campaign);

    List<Campaign> campaigns();

    Optional<Campaign> campaign(String name);

    boolean endCampaign(UUID id, long now);

    boolean deleteCampaign(UUID id);

    boolean updateCampaignCommands(UUID id, List<String> commands);

    /** Records the player's claim (once per player) and inserts their copy, in one transaction. */
    boolean claimCampaign(UUID campaignId, UUID player, MailRecord copy, long now);

    Set<UUID> claimedCampaigns(UUID player);

    int campaignClaims(UUID campaignId);

    // ── Players ─────────────────────────────────────────────────────────────

    Optional<PlayerRecord> player(UUID id);

    Optional<PlayerRecord> playerByName(String name);

    /** Creates the player record or updates their name and last seen time. */
    PlayerRecord touchPlayer(UUID id, String name, long now);

    /** Names of known players, for tab completion. */
    List<String> knownNames(int limit);

    /**
     * Delivers a daily reward once per day: the player's {@code dailyDay} must still be
     * {@code expectedDay}; it becomes {@code newDay} with the new streak, and the reward mail is inserted,
     * in one transaction.
     */
    boolean deliverDaily(UUID player, long expectedDay, long newDay, int newStreak, MailRecord reward);

    void setAcceptParcels(UUID player, boolean accept);

    boolean block(BlockEntry entry);

    boolean unblock(UUID owner, UUID blocked);

    List<BlockEntry> blocks(UUID owner);

    boolean isBlocked(UUID owner, UUID sender);

    // ── Reward codes ────────────────────────────────────────────────────────

    /** False when the code exists. */
    boolean insertCode(RewardCode code);

    Optional<RewardCode> code(String code);

    List<RewardCode> codes();

    boolean deleteCode(String code);

    boolean updateCodeCommands(String code, List<String> commands);

    int codeUses(String code, UUID player);

    /**
     * Redeems a code: checks it exists, is enabled, has not expired and has uses left, counts the use
     * (total and per player) and inserts the reward mail, all in one transaction.
     */
    RedeemOutcome redeem(String code, UUID player, MailRecord reward, long now);

    // ── Several servers (MySQL) ─────────────────────────────────────────────

    /** Highest network event id, so a server starts polling from now. */
    long latestEventId();

    List<NetworkEvent> eventsAfter(long id, int limit);

    /** Writes a network event (no-op unless several servers share the storage). */
    void publish(String type, UUID player, UUID mailId);

    void pruneEvents(long before);
}
