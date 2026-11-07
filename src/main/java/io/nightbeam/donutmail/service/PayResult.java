package io.nightbeam.donutmail.service;

/** What happened when a player paid for a cash-on-delivery parcel. {@code claim} is the delivery that follows a payment. */
public record PayResult(Status status, double price, ClaimResult claim) {
    public enum Status { PAID, NOT_WAITING, NOT_ENOUGH, NO_ECONOMY, FAILED, ERROR }

    public static PayResult of(Status status) {
        return new PayResult(status, 0, null);
    }
}
