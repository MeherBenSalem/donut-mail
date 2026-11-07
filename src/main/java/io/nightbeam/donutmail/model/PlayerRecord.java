package io.nightbeam.donutmail.model;

import java.util.UUID;

/**
 * Per-player mailbox settings and daily reward progress.
 *
 * @param dailyDay    the day number (days since 1970 in the configured time zone) of the last daily reward,
 *                    or -1 when none was delivered yet
 * @param dailyStreak consecutive days, including {@code dailyDay}
 */
public record PlayerRecord(
        UUID id,
        String name,
        long firstSeen,
        long lastSeen,
        boolean acceptParcels,
        long dailyDay,
        int dailyStreak,
        int dailyTotal
) {
    public static PlayerRecord fresh(UUID id, String name, long now) {
        return new PlayerRecord(id, name, now, now, true, -1, 0, 0);
    }
}
