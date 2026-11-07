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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Single-server storage held in memory. Every method holds one lock, so each conditional update is atomic;
 * {@link #persist(Section)} runs inside that lock, so a change is on disk before the caller acts on it.
 * {@link YamlMailStorage} adds the files.
 */
public class MemoryMailStorage implements MailStorage {

    /** Which file a change belongs to. */
    protected enum Section { MAILS, CAMPAIGNS, PLAYERS, CODES }

    protected final Map<UUID, MailRecord> mails = new LinkedHashMap<>();
    protected final Map<UUID, Campaign> campaigns = new LinkedHashMap<>();
    protected final Map<UUID, Set<UUID>> campaignClaims = new HashMap<>();
    protected final Map<UUID, PlayerRecord> players = new LinkedHashMap<>();
    protected final Map<UUID, Map<UUID, BlockEntry>> blocks = new HashMap<>();
    protected final Map<String, RewardCode> codes = new LinkedHashMap<>();
    protected final Map<String, Map<UUID, Integer>> codeUses = new HashMap<>();

    private static final Comparator<MailRecord> NEWEST_FIRST =
            Comparator.comparingLong(MailRecord::createdAt).reversed().thenComparing(r -> r.id().toString());

    @Override
    public StorageType type() {
        return StorageType.YAML;
    }

    @Override
    public synchronized void init() throws Exception {
        // Nothing to load in memory.
    }

    @Override
    public synchronized void close() {
        // Nothing to close.
    }

    /** Writes a section to disk. Called with the lock held, after every change. */
    protected void persist(Section section) {
    }

    // ── Mail: reads ─────────────────────────────────────────────────────────

    @Override
    public synchronized Optional<MailRecord> mail(UUID id) {
        return Optional.ofNullable(mails.get(id));
    }

    @Override
    public synchronized List<MailRecord> active(UUID receiver) {
        return mails.values().stream()
                .filter(m -> m.receiver().equals(receiver) && m.state().isActive())
                .sorted(NEWEST_FIRST)
                .collect(Collectors.toList());
    }

    @Override
    public synchronized List<MailRecord> sent(UUID sender, int limit) {
        return mails.values().stream()
                .filter(m -> sender.equals(m.senderId()) && m.kind() == MailKind.PARCEL)
                .sorted(NEWEST_FIRST)
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public synchronized List<MailRecord> history(UUID receiver, int limit) {
        return mails.values().stream()
                .filter(m -> m.receiver().equals(receiver) && !m.state().isActive())
                .sorted(Comparator.comparingLong(MailRecord::updatedAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public synchronized List<MailRecord> due(long now, int limit) {
        return mails.values().stream()
                .filter(m -> (m.state() == MailState.PENDING || m.state() == MailState.COD) && m.isExpired(now))
                .sorted(Comparator.comparingLong(MailRecord::expiresAt))
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public synchronized List<MailRecord> claiming(String server) {
        return mails.values().stream()
                .filter(m -> m.state() == MailState.CLAIMING && Objects.equals(server, m.claimServer()))
                .collect(Collectors.toList());
    }

    @Override
    public synchronized int countActive(UUID receiver, Set<MailKind> kinds) {
        return (int) mails.values().stream()
                .filter(m -> m.receiver().equals(receiver) && m.state().isActive() && kinds.contains(m.kind()))
                .count();
    }

    @Override
    public synchronized int countActiveFrom(UUID sender, UUID receiver) {
        return (int) mails.values().stream()
                .filter(m -> m.receiver().equals(receiver) && sender.equals(m.senderId()) && m.kind() == MailKind.PARCEL
                        && m.state().isActive())
                .count();
    }

    @Override
    public synchronized int countSentSince(UUID sender, long since) {
        return (int) mails.values().stream()
                .filter(m -> sender.equals(m.senderId()) && m.kind() == MailKind.PARCEL && m.createdAt() >= since)
                .count();
    }

    @Override
    public synchronized Map<String, Long> stats() {
        Map<String, Long> out = new LinkedHashMap<>();
        out.put("mail", (long) mails.size());
        out.put("active", mails.values().stream().filter(m -> m.state().isActive()).count());
        out.put("cod", mails.values().stream().filter(m -> m.state() == MailState.COD).count());
        out.put("claiming", mails.values().stream().filter(m -> m.state() == MailState.CLAIMING).count());
        out.put("campaigns", (long) campaigns.size());
        out.put("codes", (long) codes.size());
        out.put("players", (long) players.size());
        return out;
    }

    // ── Mail: writes ────────────────────────────────────────────────────────

    @Override
    public synchronized void insert(MailRecord mail) {
        if (mails.containsKey(mail.id())) {
            throw new StorageException("Mail " + mail.id() + " already exists");
        }
        mails.put(mail.id(), mail);
        persist(Section.MAILS);
    }

    @Override
    public synchronized void insertAll(List<MailRecord> list) {
        for (MailRecord mail : list) {
            if (mails.containsKey(mail.id())) {
                throw new StorageException("Mail " + mail.id() + " already exists");
            }
        }
        for (MailRecord mail : list) {
            mails.put(mail.id(), mail);
        }
        persist(Section.MAILS);
    }

    @Override
    public synchronized boolean lock(UUID id, UUID receiver, MailState from, ClaimLock lock) {
        MailRecord mail = mails.get(id);
        if (mail == null || mail.state() != from || (receiver != null && !mail.receiver().equals(receiver))) {
            return false;
        }
        mails.put(id, mail.toBuilder().state(MailState.CLAIMING).claimToken(lock.token()).claimServer(lock.server())
                .claimAction(lock.action()).claimAt(lock.at()).reason(from.name()).updatedAt(lock.at()).build());
        persist(Section.MAILS);
        return true;
    }

    private MailRecord locked(UUID id, String token) {
        MailRecord mail = mails.get(id);
        if (mail == null || mail.state() != MailState.CLAIMING || token == null || !token.equals(mail.claimToken())) {
            return null;
        }
        return mail;
    }

    @Override
    public synchronized boolean release(UUID id, String token, MailState back, long now) {
        MailRecord mail = locked(id, token);
        if (mail == null) {
            return false;
        }
        mails.put(id, mail.toBuilder().unlocked().state(back).reason(null).updatedAt(now).build());
        persist(Section.MAILS);
        return true;
    }

    @Override
    public synchronized boolean finishClaim(UUID id, String token, List<String> remaining, long now) {
        MailRecord mail = locked(id, token);
        if (mail == null) {
            return false;
        }
        MailRecord.Builder next = mail.toBuilder().unlocked().money(0).voucherId(null).commands(List.of())
                .readAt(mail.readAt() == 0 ? now : mail.readAt()).reason(null).updatedAt(now);
        if (remaining == null || remaining.isEmpty()) {
            next.state(MailState.CLAIMED).finishedAt(now);
        } else {
            next.state(MailState.PENDING).items(remaining);
        }
        mails.put(id, next.build());
        persist(Section.MAILS);
        return true;
    }

    @Override
    public synchronized boolean completePayment(UUID id, String token, MailRecord receipt, long now) {
        MailRecord mail = locked(id, token);
        if (mail == null || mail.codPrice() <= 0 || mail.codPaidAt() != 0 || mails.containsKey(receipt.id())) {
            return false;
        }
        mails.put(id, mail.toBuilder().unlocked().state(MailState.PENDING).codPaidAt(now).expiresAt(0)
                .reason(null).updatedAt(now).build());
        mails.put(receipt.id(), receipt);
        persist(Section.MAILS);
        return true;
    }

    @Override
    public synchronized boolean returnToSender(UUID id, Set<MailState> from, String reason, MailRecord returned,
                                               UUID requireReceiver, UUID requireSender, boolean onlyIfDue, long now) {
        MailRecord mail = mails.get(id);
        if (mail == null || !from.contains(mail.state()) || mail.codPaidAt() != 0
                || (requireReceiver != null && !requireReceiver.equals(mail.receiver()))
                || (requireSender != null && !requireSender.equals(mail.senderId()))
                || (onlyIfDue && !mail.isExpired(now))
                || mails.containsKey(returned.id())) {
            return false;
        }
        mails.put(id, mail.toBuilder().unlocked().state(MailState.RETURNED).reason(reason).finishedAt(now)
                .updatedAt(now).build());
        mails.put(returned.id(), returned);
        persist(Section.MAILS);
        return true;
    }

    @Override
    public synchronized boolean expire(UUID id, long now) {
        MailRecord mail = mails.get(id);
        if (mail == null || (mail.state() != MailState.PENDING && mail.state() != MailState.COD) || !mail.isExpired(now)) {
            return false;
        }
        mails.put(id, mail.toBuilder().state(MailState.EXPIRED).reason("EXPIRED").finishedAt(now).updatedAt(now).build());
        persist(Section.MAILS);
        return true;
    }

    @Override
    public synchronized boolean remove(UUID id, Set<MailState> from, String reason, long now) {
        MailRecord mail = mails.get(id);
        if (mail == null || !from.contains(mail.state())) {
            return false;
        }
        mails.put(id, mail.toBuilder().unlocked().state(MailState.DELETED).reason(reason).finishedAt(now)
                .updatedAt(now).build());
        persist(Section.MAILS);
        return true;
    }

    @Override
    public synchronized boolean markRead(UUID id, long now) {
        MailRecord mail = mails.get(id);
        if (mail == null || mail.readAt() != 0) {
            return false;
        }
        mails.put(id, mail.toBuilder().readAt(now).build());
        persist(Section.MAILS);
        return true;
    }

    @Override
    public synchronized int purge(long before) {
        List<UUID> doomed = mails.values().stream()
                .filter(m -> !m.state().isActive() && m.updatedAt() < before)
                .map(MailRecord::id)
                .collect(Collectors.toList());
        doomed.forEach(mails::remove);
        if (!doomed.isEmpty()) {
            persist(Section.MAILS);
        }
        return doomed.size();
    }

    // ── Campaigns ───────────────────────────────────────────────────────────

    @Override
    public synchronized boolean insertCampaign(Campaign campaign) {
        boolean taken = campaigns.values().stream().anyMatch(c -> c.name().equalsIgnoreCase(campaign.name()));
        if (taken || campaigns.containsKey(campaign.id())) {
            return false;
        }
        campaigns.put(campaign.id(), campaign);
        persist(Section.CAMPAIGNS);
        return true;
    }

    @Override
    public synchronized List<Campaign> campaigns() {
        return new ArrayList<>(campaigns.values());
    }

    @Override
    public synchronized Optional<Campaign> campaign(String name) {
        return campaigns.values().stream().filter(c -> c.name().equalsIgnoreCase(name)).findFirst();
    }

    @Override
    public synchronized boolean endCampaign(UUID id, long now) {
        Campaign campaign = campaigns.get(id);
        if (campaign == null || campaign.endedAt() != 0) {
            return false;
        }
        campaigns.put(id, campaign.withEndedAt(now));
        persist(Section.CAMPAIGNS);
        return true;
    }

    @Override
    public synchronized boolean deleteCampaign(UUID id) {
        if (campaigns.remove(id) == null) {
            return false;
        }
        campaignClaims.remove(id);
        persist(Section.CAMPAIGNS);
        return true;
    }

    @Override
    public synchronized boolean updateCampaignCommands(UUID id, List<String> commands) {
        Campaign campaign = campaigns.get(id);
        if (campaign == null) {
            return false;
        }
        campaigns.put(id, campaign.withCommands(commands));
        persist(Section.CAMPAIGNS);
        return true;
    }

    @Override
    public synchronized boolean claimCampaign(UUID campaignId, UUID player, MailRecord copy, long now) {
        Campaign campaign = campaigns.get(campaignId);
        if (campaign == null || !campaign.isLive(now) || mails.containsKey(copy.id())) {
            return false;
        }
        Set<UUID> claimed = campaignClaims.computeIfAbsent(campaignId, ignored -> new HashSet<>());
        if (!claimed.add(player)) {
            return false;
        }
        mails.put(copy.id(), copy);
        persist(Section.CAMPAIGNS);
        persist(Section.MAILS);
        return true;
    }

    @Override
    public synchronized Set<UUID> claimedCampaigns(UUID player) {
        Set<UUID> out = new HashSet<>();
        campaignClaims.forEach((campaign, claimers) -> {
            if (claimers.contains(player)) {
                out.add(campaign);
            }
        });
        return out;
    }

    @Override
    public synchronized int campaignClaims(UUID campaignId) {
        return campaignClaims.getOrDefault(campaignId, Set.of()).size();
    }

    // ── Players ─────────────────────────────────────────────────────────────

    @Override
    public synchronized Optional<PlayerRecord> player(UUID id) {
        return Optional.ofNullable(players.get(id));
    }

    @Override
    public synchronized Optional<PlayerRecord> playerByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return players.values().stream()
                .filter(p -> p.name() != null && p.name().equalsIgnoreCase(name))
                .max(Comparator.comparingLong(PlayerRecord::lastSeen));
    }

    @Override
    public synchronized PlayerRecord touchPlayer(UUID id, String name, long now) {
        PlayerRecord current = players.get(id);
        PlayerRecord next = current == null
                ? PlayerRecord.fresh(id, name, now)
                : new PlayerRecord(id, name == null ? current.name() : name, current.firstSeen(), now,
                current.acceptParcels(), current.dailyDay(), current.dailyStreak(), current.dailyTotal());
        players.put(id, next);
        persist(Section.PLAYERS);
        return next;
    }

    @Override
    public synchronized List<String> knownNames(int limit) {
        return players.values().stream()
                .sorted(Comparator.comparingLong(PlayerRecord::lastSeen).reversed())
                .map(PlayerRecord::name)
                .filter(Objects::nonNull)
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public synchronized boolean deliverDaily(UUID player, long expectedDay, long newDay, int newStreak, MailRecord reward) {
        PlayerRecord current = players.get(player);
        if (current == null || current.dailyDay() != expectedDay || mails.containsKey(reward.id())) {
            return false;
        }
        players.put(player, new PlayerRecord(player, current.name(), current.firstSeen(), current.lastSeen(),
                current.acceptParcels(), newDay, newStreak, current.dailyTotal() + 1));
        mails.put(reward.id(), reward);
        persist(Section.PLAYERS);
        persist(Section.MAILS);
        return true;
    }

    @Override
    public synchronized void setAcceptParcels(UUID player, boolean accept) {
        PlayerRecord current = players.get(player);
        if (current == null) {
            current = PlayerRecord.fresh(player, null, System.currentTimeMillis());
        }
        players.put(player, new PlayerRecord(player, current.name(), current.firstSeen(), current.lastSeen(), accept,
                current.dailyDay(), current.dailyStreak(), current.dailyTotal()));
        persist(Section.PLAYERS);
    }

    @Override
    public synchronized boolean block(BlockEntry entry) {
        Map<UUID, BlockEntry> list = blocks.computeIfAbsent(entry.owner(), ignored -> new LinkedHashMap<>());
        if (list.containsKey(entry.blocked())) {
            return false;
        }
        list.put(entry.blocked(), entry);
        persist(Section.PLAYERS);
        return true;
    }

    @Override
    public synchronized boolean unblock(UUID owner, UUID blocked) {
        Map<UUID, BlockEntry> list = blocks.get(owner);
        if (list == null || list.remove(blocked) == null) {
            return false;
        }
        persist(Section.PLAYERS);
        return true;
    }

    @Override
    public synchronized List<BlockEntry> blocks(UUID owner) {
        return new ArrayList<>(blocks.getOrDefault(owner, Map.of()).values());
    }

    @Override
    public synchronized boolean isBlocked(UUID owner, UUID sender) {
        return blocks.getOrDefault(owner, Map.of()).containsKey(sender);
    }

    // ── Reward codes ────────────────────────────────────────────────────────

    @Override
    public synchronized boolean insertCode(RewardCode code) {
        if (codes.containsKey(code.code())) {
            return false;
        }
        codes.put(code.code(), code);
        persist(Section.CODES);
        return true;
    }

    @Override
    public synchronized Optional<RewardCode> code(String code) {
        return Optional.ofNullable(codes.get(RewardCode.normalize(code)));
    }

    @Override
    public synchronized List<RewardCode> codes() {
        return new ArrayList<>(codes.values());
    }

    @Override
    public synchronized boolean deleteCode(String code) {
        String key = RewardCode.normalize(code);
        if (codes.remove(key) == null) {
            return false;
        }
        codeUses.remove(key);
        persist(Section.CODES);
        return true;
    }

    @Override
    public synchronized boolean updateCodeCommands(String code, List<String> commands) {
        String key = RewardCode.normalize(code);
        RewardCode current = codes.get(key);
        if (current == null) {
            return false;
        }
        codes.put(key, current.withCommands(commands));
        persist(Section.CODES);
        return true;
    }

    @Override
    public synchronized int codeUses(String code, UUID player) {
        return codeUses.getOrDefault(RewardCode.normalize(code), Map.of()).getOrDefault(player, 0);
    }

    @Override
    public synchronized RedeemOutcome redeem(String code, UUID player, MailRecord reward, long now) {
        String key = RewardCode.normalize(code);
        RewardCode current = codes.get(key);
        if (current == null) {
            return RedeemOutcome.UNKNOWN;
        }
        if (!current.enabled()) {
            return RedeemOutcome.DISABLED;
        }
        if (current.isExpired(now)) {
            return RedeemOutcome.EXPIRED;
        }
        if (current.usedUp()) {
            return RedeemOutcome.USED_UP;
        }
        Map<UUID, Integer> uses = codeUses.computeIfAbsent(key, ignored -> new HashMap<>());
        int mine = uses.getOrDefault(player, 0);
        if (current.perPlayer() > 0 && mine >= current.perPlayer()) {
            return RedeemOutcome.ALREADY_REDEEMED;
        }
        if (mails.containsKey(reward.id())) {
            throw new StorageException("Mail " + reward.id() + " already exists");
        }
        uses.put(player, mine + 1);
        codes.put(key, current.withUses(current.uses() + 1));
        mails.put(reward.id(), reward);
        persist(Section.CODES);
        persist(Section.MAILS);
        return RedeemOutcome.OK;
    }

    // ── Several servers ─────────────────────────────────────────────────────

    @Override
    public long latestEventId() {
        return 0;
    }

    @Override
    public List<NetworkEvent> eventsAfter(long id, int limit) {
        return List.of();
    }

    @Override
    public void publish(String type, UUID player, UUID mailId) {
        // One server: nothing to tell.
    }

    @Override
    public void pruneEvents(long before) {
    }

    protected static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
