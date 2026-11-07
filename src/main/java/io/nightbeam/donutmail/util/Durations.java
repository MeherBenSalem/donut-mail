package io.nightbeam.donutmail.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsing and formatting of durations ("7d", "1d12h", "30m"), dates and amounts ("2.5k"). */
public final class Durations {
    private static final Pattern PART = Pattern.compile("(\\d+)\\s*(w|d|h|m|s)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHOLE = Pattern.compile("(\\s*\\d+\\s*[wdhms]\\s*)+", Pattern.CASE_INSENSITIVE);

    private Durations() {
    }

    /** Milliseconds, or -1 when the text is not a duration. */
    public static long parse(String text) {
        if (text == null || !WHOLE.matcher(text).matches()) {
            return -1;
        }
        Matcher matcher = PART.matcher(text);
        long total = 0;
        while (matcher.find()) {
            long value = Long.parseLong(matcher.group(1));
            total += switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "w" -> value * 7L * 86_400_000L;
                case "d" -> value * 86_400_000L;
                case "h" -> value * 3_600_000L;
                case "m" -> value * 60_000L;
                default -> value * 1_000L;
            };
        }
        return total;
    }

    /**
     * A point in time: a duration from now ("7d"), a date ("2026-12-24", midnight) or a date and time
     * ("2026-12-24T18:00") in {@code zone}. Returns -1 when it cannot be read.
     */
    public static long parseMoment(String text, long now, ZoneId zone) {
        if (text == null || text.isBlank()) return -1;
        String value = text.trim();
        if (value.startsWith("+")) value = value.substring(1);
        long duration = parse(value);
        if (duration >= 0) return now + duration;
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zone).toInstant().toEpochMilli();
        } catch (DateTimeParseException notDateTime) {
            try {
                return LocalDate.parse(value).atStartOfDay(zone).toInstant().toEpochMilli();
            } catch (DateTimeParseException notDate) {
                return -1;
            }
        }
    }

    /** "2d 4h", "35m", "12s". */
    public static String format(long millis) {
        if (millis <= 0) return "0s";
        long seconds = millis / 1000;
        long days = seconds / 86_400;
        long hours = (seconds % 86_400) / 3_600;
        long minutes = (seconds % 3_600) / 60;
        long secs = seconds % 60;
        if (days > 0) return days + "d" + (hours > 0 ? " " + hours + "h" : "");
        if (hours > 0) return hours + "h" + (minutes > 0 ? " " + minutes + "m" : "");
        if (minutes > 0) return minutes + "m";
        return secs + "s";
    }

    /** An amount of money: "250", "1,250", "2.5k", "1m", "3b". Returns NaN when invalid. */
    public static double parseAmount(String text) {
        if (text == null) return Double.NaN;
        String value = text.trim().replace(",", "").replace("_", "").toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return Double.NaN;
        double multiplier = 1;
        char last = value.charAt(value.length() - 1);
        if (last == 'k' || last == 'm' || last == 'b' || last == 't') {
            multiplier = switch (last) {
                case 'k' -> 1_000d;
                case 'm' -> 1_000_000d;
                case 'b' -> 1_000_000_000d;
                default -> 1_000_000_000_000d;
            };
            value = value.substring(0, value.length() - 1);
        }
        try {
            double amount = Double.parseDouble(value) * multiplier;
            if (Double.isNaN(amount) || Double.isInfinite(amount)) return Double.NaN;
            return Math.round(amount * 100d) / 100d;
        } catch (NumberFormatException invalid) {
            return Double.NaN;
        }
    }
}
