package io.nightbeam.donutmail.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One mail as stored. Immutable: storage returns snapshots and changes are made with conditional updates.
 *
 * <p>Items are kept in their stored form (see {@code ItemSerializer}); the storage layer never needs a
 * running server to read or write them.
 */
public final class MailRecord {
    private final UUID id;
    private final MailKind kind;
    private final UUID receiver;
    private final String receiverName;
    private final UUID senderId;
    private final String senderName;
    private final String message;
    private final List<String> items;
    private final double money;
    private final String voucherId;
    private final List<String> commands;
    private final double codPrice;
    private final long codPaidAt;
    private final MailState state;
    private final long createdAt;
    private final long expiresAt;
    private final long finishedAt;
    private final long readAt;
    private final long updatedAt;
    private final String claimToken;
    private final String claimServer;
    private final String claimAction;
    private final long claimAt;
    private final String reason;
    private final UUID refId;
    private final UUID campaignId;

    private MailRecord(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.kind = Objects.requireNonNull(b.kind, "kind");
        this.receiver = Objects.requireNonNull(b.receiver, "receiver");
        this.receiverName = b.receiverName;
        this.senderId = b.senderId;
        this.senderName = b.senderName == null ? "Server" : b.senderName;
        this.message = b.message == null ? "" : b.message;
        this.items = b.items == null ? List.of() : List.copyOf(b.items);
        this.money = b.money;
        this.voucherId = b.voucherId == null || b.voucherId.isBlank() ? null : b.voucherId;
        this.commands = b.commands == null ? List.of() : List.copyOf(b.commands);
        this.codPrice = b.codPrice;
        this.codPaidAt = b.codPaidAt;
        this.state = Objects.requireNonNull(b.state, "state");
        this.createdAt = b.createdAt;
        this.expiresAt = b.expiresAt;
        this.finishedAt = b.finishedAt;
        this.readAt = b.readAt;
        this.updatedAt = b.updatedAt;
        this.claimToken = b.claimToken;
        this.claimServer = b.claimServer;
        this.claimAction = b.claimAction;
        this.claimAt = b.claimAt;
        this.reason = b.reason;
        this.refId = b.refId;
        this.campaignId = b.campaignId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.id = id;
        b.kind = kind;
        b.receiver = receiver;
        b.receiverName = receiverName;
        b.senderId = senderId;
        b.senderName = senderName;
        b.message = message;
        b.items = items;
        b.money = money;
        b.voucherId = voucherId;
        b.commands = commands;
        b.codPrice = codPrice;
        b.codPaidAt = codPaidAt;
        b.state = state;
        b.createdAt = createdAt;
        b.expiresAt = expiresAt;
        b.finishedAt = finishedAt;
        b.readAt = readAt;
        b.updatedAt = updatedAt;
        b.claimToken = claimToken;
        b.claimServer = claimServer;
        b.claimAction = claimAction;
        b.claimAt = claimAt;
        b.reason = reason;
        b.refId = refId;
        b.campaignId = campaignId;
        return b;
    }

    public UUID id() { return id; }
    public MailKind kind() { return kind; }
    public UUID receiver() { return receiver; }
    public String receiverName() { return receiverName; }
    public UUID senderId() { return senderId; }
    public String senderName() { return senderName; }
    public String message() { return message; }
    public List<String> items() { return items; }
    public double money() { return money; }
    public String voucherId() { return voucherId; }
    public List<String> commands() { return commands; }
    public double codPrice() { return codPrice; }
    public long codPaidAt() { return codPaidAt; }
    public MailState state() { return state; }
    public long createdAt() { return createdAt; }
    public long expiresAt() { return expiresAt; }
    public long finishedAt() { return finishedAt; }
    public long readAt() { return readAt; }
    public long updatedAt() { return updatedAt; }
    public String claimToken() { return claimToken; }
    public String claimServer() { return claimServer; }
    public String claimAction() { return claimAction; }
    public long claimAt() { return claimAt; }
    public String reason() { return reason; }
    public UUID refId() { return refId; }
    public UUID campaignId() { return campaignId; }

    /** A cash-on-delivery parcel that has not been paid yet. */
    public boolean awaitingPayment() {
        return codPrice > 0 && codPaidAt == 0;
    }

    public boolean isExpired(long now) {
        return expiresAt > 0 && now >= expiresAt;
    }

    public boolean isUnread() {
        return readAt == 0 && state.isActive();
    }

    /** Anything to hand over besides text: items, money, a voucher or commands. */
    public boolean hasRewards() {
        return !items.isEmpty() || money > 0 || voucherId != null || !commands.isEmpty();
    }

    /** Sent by a player (so it can go back to them). */
    public boolean fromPlayer() {
        return senderId != null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MailRecord record && record.id.equals(id) && record.state == state
                && record.updatedAt == updatedAt && Objects.equals(record.claimToken, claimToken)
                && record.items.equals(items) && record.readAt == readAt;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Mail{" + id + " " + kind + " " + state + " to=" + receiver + " from=" + senderName + " items=" + items.size()
                + (codPrice > 0 ? " cod=" + codPrice : "") + "}";
    }

    public static final class Builder {
        private UUID id = UUID.randomUUID();
        private MailKind kind = MailKind.DELIVERY;
        private UUID receiver;
        private String receiverName;
        private UUID senderId;
        private String senderName;
        private String message;
        private List<String> items;
        private double money;
        private String voucherId;
        private List<String> commands;
        private double codPrice;
        private long codPaidAt;
        private MailState state = MailState.PENDING;
        private long createdAt = System.currentTimeMillis();
        private long expiresAt;
        private long finishedAt;
        private long readAt;
        private long updatedAt = createdAt;
        private String claimToken;
        private String claimServer;
        private String claimAction;
        private long claimAt;
        private String reason;
        private UUID refId;
        private UUID campaignId;

        public Builder id(UUID value) { this.id = value; return this; }
        public Builder kind(MailKind value) { this.kind = value; return this; }
        public Builder receiver(UUID value) { this.receiver = value; return this; }
        public Builder receiverName(String value) { this.receiverName = value; return this; }
        public Builder senderId(UUID value) { this.senderId = value; return this; }
        public Builder senderName(String value) { this.senderName = value; return this; }
        public Builder message(String value) { this.message = value; return this; }
        public Builder items(List<String> value) { this.items = value; return this; }
        public Builder money(double value) { this.money = value; return this; }
        public Builder voucherId(String value) { this.voucherId = value; return this; }
        public Builder commands(List<String> value) { this.commands = value; return this; }
        public Builder codPrice(double value) { this.codPrice = value; return this; }
        public Builder codPaidAt(long value) { this.codPaidAt = value; return this; }
        public Builder state(MailState value) { this.state = value; return this; }
        public Builder createdAt(long value) { this.createdAt = value; return this; }
        public Builder expiresAt(long value) { this.expiresAt = value; return this; }
        public Builder finishedAt(long value) { this.finishedAt = value; return this; }
        public Builder readAt(long value) { this.readAt = value; return this; }
        public Builder updatedAt(long value) { this.updatedAt = value; return this; }
        public Builder claimToken(String value) { this.claimToken = value; return this; }
        public Builder claimServer(String value) { this.claimServer = value; return this; }
        public Builder claimAction(String value) { this.claimAction = value; return this; }
        public Builder claimAt(long value) { this.claimAt = value; return this; }
        public Builder reason(String value) { this.reason = value; return this; }
        public Builder refId(UUID value) { this.refId = value; return this; }
        public Builder campaignId(UUID value) { this.campaignId = value; return this; }

        /** Clears the claim lock fields. */
        public Builder unlocked() {
            this.claimToken = null;
            this.claimServer = null;
            this.claimAction = null;
            this.claimAt = 0;
            return this;
        }

        public MailRecord build() {
            return new MailRecord(this);
        }
    }
}
