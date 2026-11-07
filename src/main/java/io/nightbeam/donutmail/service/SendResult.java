package io.nightbeam.donutmail.service;

import java.util.UUID;

/** What happened when a parcel was sent. {@code detail} is the limit, seconds or fee that applied. */
public record SendResult(Status status, UUID mailId, String detail) {
    public enum Status {
        SENT,
        DISABLED,
        SELF,
        UNKNOWN_RECIPIENT,
        BLOCKED,
        REFUSING,
        COOLDOWN,
        DAILY_LIMIT,
        RECIPIENT_LIMIT,
        MAILBOX_FULL,
        BLACKLISTED,
        EMPTY,
        COD_DISABLED,
        COD_PRICE,
        NO_ECONOMY,
        POSTAGE,
        CANCELLED,
        ERROR
    }

    public static SendResult of(Status status) {
        return new SendResult(status, null, "");
    }

    public static SendResult of(Status status, String detail) {
        return new SendResult(status, null, detail);
    }

    public boolean sent() {
        return status == Status.SENT;
    }
}
