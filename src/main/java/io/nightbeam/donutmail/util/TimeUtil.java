package io.nightbeam.donutmail.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private TimeUtil() {
    }

    public static String format(long epochMillis) {
        if (epochMillis <= 0) {
            return "N/A";
        }
        return FORMATTER.format(Instant.ofEpochMilli(epochMillis));
    }
}
