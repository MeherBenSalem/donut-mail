package io.nightbeam.donutmail.model;

/** What a mail is. The kind decides the mailbox tab and how it expires. */
public enum MailKind {
    /** Text from staff or the console. */
    MESSAGE(Tab.INBOX),
    /** Items, money or rewards delivered by staff, the console or another plugin. */
    DELIVERY(Tab.INBOX),
    /** Items sent by a player (optionally cash on delivery). */
    PARCEL(Tab.PARCELS),
    /** Payment for a cash-on-delivery parcel, sent to the parcel's sender. */
    RECEIPT(Tab.INBOX),
    /** A parcel coming back to its sender (declined, expired, recalled or removed). */
    RETURN(Tab.INBOX),
    /** A player's copy of a server campaign, created when they claim it. */
    CAMPAIGN(Tab.INBOX),
    /** A daily login reward. */
    DAILY(Tab.REWARDS),
    /** A redeemed reward code. */
    CODE(Tab.REWARDS),
    /** A vote reward. */
    VOTE(Tab.REWARDS),
    /** A reward delivered by another plugin through the API. */
    REWARD(Tab.REWARDS);

    private final Tab tab;

    MailKind(Tab tab) {
        this.tab = tab;
    }

    public Tab tab() {
        return tab;
    }

    public static MailKind parse(String value, MailKind fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return MailKind.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return fallback;
        }
    }

    public enum Tab {
        INBOX,
        PARCELS,
        SENT,
        REWARDS
    }
}
