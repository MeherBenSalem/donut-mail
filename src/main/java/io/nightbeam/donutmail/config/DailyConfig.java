package io.nightbeam.donutmail.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.logging.Logger;

/** daily.yml: the streak calendar. */
public final class DailyConfig {
    public final boolean enabled;
    public final ZoneId zone;
    public final boolean resetOnMiss;
    public final boolean loop;
    public final List<Day> days;

    public DailyConfig(YamlConfiguration yaml, Logger logger) {
        this.enabled = yaml.getBoolean("enabled", true);
        String zoneName = yaml.getString("timezone", "");
        ZoneId resolved = ZoneId.systemDefault();
        if (zoneName != null && !zoneName.isBlank()) {
            try {
                resolved = ZoneId.of(zoneName.trim());
            } catch (RuntimeException invalid) {
                logger.warning("daily.yml: unknown timezone '" + zoneName + "', using " + resolved);
            }
        }
        this.zone = resolved;
        this.resetOnMiss = yaml.getBoolean("reset-on-miss", true);
        this.loop = !"LAST".equalsIgnoreCase(yaml.getString("after-last-day", "LOOP"));
        TreeMap<Integer, Day> sorted = new TreeMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("days");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection day = section.getConfigurationSection(key);
                int number;
                try {
                    number = Integer.parseInt(key.trim());
                } catch (NumberFormatException invalid) {
                    logger.warning("daily.yml: day '" + key + "' is not a number and was skipped.");
                    continue;
                }
                if (day == null) continue;
                Material icon = Material.matchMaterial(day.getString("icon", "CHEST").toUpperCase(Locale.ROOT));
                sorted.put(number, new Day(number, icon == null ? Material.CHEST : icon, day.getStringList("display"),
                        day.getStringList("items"), Math.max(0, day.getDouble("money", 0)), day.getStringList("commands"),
                        day.getString("voucher", "")));
            }
        }
        this.days = new ArrayList<>(sorted.values());
    }

    public boolean active() {
        return enabled && !days.isEmpty();
    }

    /** The calendar position (0-based) for a streak (1-based). */
    public int indexFor(int streak) {
        if (days.isEmpty()) return 0;
        int position = Math.max(1, streak) - 1;
        return loop ? position % days.size() : Math.min(position, days.size() - 1);
    }

    public Day dayFor(int streak) {
        return days.get(indexFor(streak));
    }

    /**
     * The streak after a delivery today.
     *
     * @param lastDay the day of the previous delivery (-1 = none)
     * @param today   today's day number
     */
    public int nextStreak(long lastDay, int streak, long today) {
        if (lastDay < 0 || streak <= 0) return 1;
        if (lastDay == today - 1) return streak + 1;
        return resetOnMiss ? 1 : streak + 1;
    }

    public record Day(int number, Material icon, List<String> display, List<String> items, double money,
                      List<String> commands, String voucher) {
    }
}
