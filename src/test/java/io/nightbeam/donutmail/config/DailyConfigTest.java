package io.nightbeam.donutmail.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyConfigTest {
    private static DailyConfig load(String extra) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("enabled: true\ntimezone: UTC\n" + extra + "days:\n"
                + "  1: {icon: IRON_INGOT, items: ['IRON_INGOT:16']}\n"
                + "  2: {icon: GOLD_INGOT, money: 250}\n"
                + "  3: {icon: DIAMOND, items: ['DIAMOND:3']}\n");
        return new DailyConfig(yaml, Logger.getLogger("test"));
    }

    @Test
    void streakMovesOnConsecutiveDays() throws Exception {
        DailyConfig daily = load("");
        assertTrue(daily.active());
        assertEquals(3, daily.days.size());
        assertEquals(1, daily.nextStreak(-1, 0, 100), "first reward");
        assertEquals(4, daily.nextStreak(99, 3, 100), "yesterday: +1");
        assertEquals(1, daily.nextStreak(95, 3, 100), "missed days reset");
        assertEquals(250, daily.dayFor(2).money(), 0.0);
    }

    @Test
    void loopsOrStaysOnTheLastDay() throws Exception {
        DailyConfig loop = load("after-last-day: LOOP\n");
        assertEquals(0, loop.indexFor(1));
        assertEquals(2, loop.indexFor(3));
        assertEquals(0, loop.indexFor(4));
        assertEquals(1, loop.indexFor(5));
        DailyConfig last = load("after-last-day: LAST\nreset-on-miss: false\n");
        assertEquals(2, last.indexFor(9));
        assertEquals(4, last.nextStreak(90, 3, 100), "no reset: the streak only pauses");
    }

    @Test
    void emptyCalendarIsOff() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("enabled: true\ndays: {}\n");
        assertFalse(new DailyConfig(yaml, Logger.getLogger("test")).active());
    }
}
