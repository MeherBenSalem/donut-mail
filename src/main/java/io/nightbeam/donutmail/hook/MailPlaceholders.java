package io.nightbeam.donutmail.hook;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.config.DailyConfig;
import io.nightbeam.donutmail.service.DailyService;
import io.nightbeam.donutmail.service.SummaryCache;
import io.nightbeam.donutmail.util.Durations;
import io.nightbeam.donutmail.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

/**
 * %donutmail_unread%, _waiting%, _parcels%, _cod%, _rewards%, _inbox%, _campaigns%, _daily_streak%,
 * _daily_next%, _daily_next_day%, _daily_ready%, _daily_time%. Counts come from a cache of online players,
 * so placeholders never touch the database.
 */
public final class MailPlaceholders extends PlaceholderExpansion {
    private final DonutMailPlugin plugin;

    public MailPlaceholders(DonutMailPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "donutmail";
    }

    @Override
    public String getAuthor() {
        return "NightBeam";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";
        SummaryCache.Summary summary = plugin.summaries().get(player.getUniqueId());
        String key = params.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "unread" -> String.valueOf(summary.unread());
            case "waiting", "total" -> String.valueOf(summary.waiting());
            case "parcels" -> String.valueOf(summary.parcels());
            case "cod" -> String.valueOf(summary.cod());
            case "rewards" -> String.valueOf(summary.rewards());
            case "inbox" -> String.valueOf(summary.inbox());
            case "campaigns" -> String.valueOf(summary.campaigns());
            case "daily_streak" -> String.valueOf(summary.streak());
            case "daily_ready" -> String.valueOf(summary.dailyDay() == plugin.dailyRewards().today());
            case "daily_next", "daily_next_day", "daily_time" -> daily(summary, key);
            default -> null;
        };
    }

    /** The next reward not delivered yet: tomorrow's after today's arrived, otherwise today's. */
    private String daily(SummaryCache.Summary summary, String key) {
        DailyConfig daily = plugin.daily();
        if (!daily.active()) return "";
        DailyService service = plugin.dailyRewards();
        boolean deliveredToday = summary.dailyDay() == service.today();
        int streak = deliveredToday ? summary.streak() + 1 : daily.nextStreak(summary.dailyDay(), summary.streak(), service.today());
        DailyConfig.Day day = daily.dayFor(streak);
        return switch (key) {
            case "daily_next_day" -> String.valueOf(day.number());
            case "daily_time" -> deliveredToday ? Durations.format(service.untilTomorrow()) : "0s";
            default -> day.display().isEmpty() ? "Day " + day.number() : Text.plain(String.join(", ", day.display()));
        };
    }
}
