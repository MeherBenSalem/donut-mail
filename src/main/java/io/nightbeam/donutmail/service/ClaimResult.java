package io.nightbeam.donutmail.service;

/**
 * What happened when a player claimed a mail.
 *
 * @param delivered  stacks handed over
 * @param left       stacks that stay in the mailbox
 * @param unreadable stacks this server version cannot read (they stay)
 */
public record ClaimResult(Status status, int delivered, int left, double money, int unreadable, boolean rewards) {
    public enum Status {
        CLAIMED,
        PARTIAL,
        INVENTORY_FULL,
        GONE,
        EXPIRED,
        WAITING_PAYMENT,
        NEEDS_ECONOMY,
        VOUCHER_MISSING,
        OFFLINE,
        ERROR
    }

    public static ClaimResult of(Status status) {
        return new ClaimResult(status, 0, 0, 0, 0, false);
    }

    public boolean success() {
        return status == Status.CLAIMED || status == Status.PARTIAL;
    }
}
