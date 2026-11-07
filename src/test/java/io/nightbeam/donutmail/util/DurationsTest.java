package io.nightbeam.donutmail.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationsTest {
    @Test
    void parsesDurations() {
        assertEquals(7 * 86_400_000L, Durations.parse("7d"));
        assertEquals(86_400_000L + 12 * 3_600_000L, Durations.parse("1d12h"));
        assertEquals(30 * 60_000L, Durations.parse("30m"));
        assertEquals(14 * 86_400_000L, Durations.parse("2w"));
        assertEquals(-1, Durations.parse("soon"));
        assertEquals(-1, Durations.parse("7x"));
    }

    @Test
    void parsesMoments() {
        long now = 1_000_000L;
        assertEquals(now + 3_600_000L, Durations.parseMoment("1h", now, ZoneOffset.UTC));
        assertEquals(now + 3_600_000L, Durations.parseMoment("+1h", now, ZoneOffset.UTC));
        long christmas = LocalDate.of(2026, 12, 24).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        assertEquals(christmas, Durations.parseMoment("2026-12-24", now, ZoneOffset.UTC));
        assertEquals(christmas + 18 * 3_600_000L, Durations.parseMoment("2026-12-24T18:00", now, ZoneOffset.UTC));
        assertEquals(-1, Durations.parseMoment("tomorrowish", now, ZoneOffset.UTC));
    }

    @Test
    void formats() {
        assertEquals("2d 4h", Durations.format(2 * 86_400_000L + 4 * 3_600_000L + 5_000));
        assertEquals("35m", Durations.format(35 * 60_000L));
        assertEquals("1h 1m", Durations.format(61 * 60_000L));
        assertEquals("12s", Durations.format(12_000));
        assertEquals("0s", Durations.format(-5));
    }

    @Test
    void parsesAmounts() {
        assertEquals(250, Durations.parseAmount("250"));
        assertEquals(1250, Durations.parseAmount("1,250"));
        assertEquals(2500, Durations.parseAmount("2.5k"));
        assertEquals(1_000_000, Durations.parseAmount("1m"));
        assertEquals(3_000_000_000d, Durations.parseAmount("3B"));
        assertEquals(0.01, Durations.parseAmount("0.009"), 0.0001);
        assertTrue(Double.isNaN(Durations.parseAmount("abc")));
        assertTrue(Double.isNaN(Durations.parseAmount("")));
        assertTrue(Double.isNaN(Durations.parseAmount("NaN")));
    }
}
