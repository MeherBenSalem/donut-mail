package io.nightbeam.donutmail.model;

import java.util.UUID;

/**
 * Who holds a mail while an action runs. The token proves ownership: only the holder can finish or
 * release the lock.
 */
public record ClaimLock(String token, String server, String action, long at) {
    public static final String CLAIM = "CLAIM";
    public static final String PAY = "PAY";

    public static ClaimLock create(String server, String action, long now) {
        return new ClaimLock(UUID.randomUUID().toString(), server, action, now);
    }
}
