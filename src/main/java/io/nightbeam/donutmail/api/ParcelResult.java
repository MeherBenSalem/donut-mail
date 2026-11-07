package io.nightbeam.donutmail.api;

import java.util.UUID;

/** The outcome of {@link DonutMailAPI#sendParcel}. {@code mailId} is set when it was sent. */
public record ParcelResult(Status status, UUID mailId) {
    public enum Status { SENT, BLOCKED, REFUSING, CANCELLED, NO_ECONOMY, INVALID, FAILED }

    public boolean sent() {
        return status == Status.SENT;
    }
}
