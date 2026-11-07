package io.nightbeam.donutmail.model;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * The life of a mail. Every change between states is one conditional update in storage, so only one
 * action (claim, pay, decline, expire, recall, remove) can ever win.
 */
public enum MailState {
    /** Waiting in the mailbox and claimable. */
    PENDING,
    /** A cash-on-delivery parcel waiting for the recipient to pay or decline. */
    COD,
    /** Locked by an action in progress (claim or payment). Holds a claim token. */
    CLAIMING,
    /** Everything was delivered. */
    CLAIMED,
    /** The items went back to the sender as a new mail. */
    RETURNED,
    /** Expired without a sender to return to. */
    EXPIRED,
    /** Removed by staff. */
    DELETED;

    public static final Set<MailState> ACTIVE = EnumSet.of(PENDING, COD, CLAIMING);
    public static final Set<MailState> FINISHED = EnumSet.of(CLAIMED, RETURNED, EXPIRED, DELETED);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public static MailState parse(String value, MailState fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return MailState.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return fallback;
        }
    }
}
