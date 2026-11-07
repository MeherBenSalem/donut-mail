package io.nightbeam.donutmail.service;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.api.event.DailyRewardEvent;
import io.nightbeam.donutmail.config.DailyConfig;
import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.PlayerRecord;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * Daily login rewards. On a player's first join of the day the reward for their streak day goes to the
 * Rewards tab. The player's day and streak move forward in the same transaction as the reward is saved,
 * so two servers (or two quick joins) can never deliver the same day twice.
 */
public final class DailyService {
    private final DonutMailPlugin plugin;

    public DailyService(DonutMailPlugin plugin) {
        this.plugin = plugin;
    }

    public long today() {
        return LocalDate.now(plugin.daily().zone).toEpochDay();
    }

    /** Milliseconds until the next day starts in the configured time zone. */
    public long untilTomorrow() {
        DailyConfig daily = plugin.daily();
        ZonedDateTime now = ZonedDateTime.now(daily.zone);
        return LocalDate.now(daily.zone).plusDays(1).atStartOfDay(daily.zone).toInstant().toEpochMilli() - now.toInstant().toEpochMilli();
    }

    /** Called on a storage thread when a player joins, with their up-to-date record. */
    public void onJoin(Player player, PlayerRecord record) {
        DailyConfig daily = plugin.daily();
        if (!daily.active() || !player.hasPermission("donutmail.daily")) return;
        PlayerRecord current = record;
        for (int attempt = 0; attempt < 2 && current != null; attempt++) {
            long today = today();
            if (current.dailyDay() == today) return;
            int streak = daily.nextStreak(current.dailyDay(), current.dailyStreak(), today);
            DailyConfig.Day day = daily.dayFor(streak);
            MailRecord reward = plugin.mail().serverMail(MailKind.DAILY, player.getUniqueId(), "Daily Rewards")
                    .receiverName(player.getName())
                    .message(plugin.messages().format("mail.daily", "day", String.valueOf(day.number()), "streak", String.valueOf(streak)))
                    .items(Rewards.encoded(day.items(), plugin.getLogger(), "daily.yml day " + day.number()))
                    .money(day.money()).commands(day.commands()).voucherId(day.voucher())
                    .build();
            if (plugin.storage().deliverDaily(player.getUniqueId(), current.dailyDay(), today, streak, reward)) {
                plugin.audit().log("DAILY", "player", player.getName(), "day", day.number(), "streak", streak, "mail", reward.id());
                plugin.fireAsync(new DailyRewardEvent(player.getUniqueId(), reward.id(), day.number(), streak));
                plugin.notifier().dailyDelivered(player, day.number(), streak);
                plugin.summaries().refresh(player.getUniqueId());
                return;
            }
            // Another server (or join) moved the day first: read it again.
            current = plugin.storage().player(player.getUniqueId()).orElse(null);
        }
    }

    /** Calendar position (0-based) of the next delivery and whether today's reward was delivered. */
    public Position position(PlayerRecord record) {
        DailyConfig daily = plugin.daily();
        long today = today();
        if (record == null || record.dailyDay() < 0) {
            return new Position(daily.indexFor(1), false, 0);
        }
        if (record.dailyDay() == today) {
            return new Position(daily.indexFor(record.dailyStreak()), true, record.dailyStreak());
        }
        int next = daily.nextStreak(record.dailyDay(), record.dailyStreak(), today);
        int shownStreak = next == 1 ? 0 : record.dailyStreak();
        return new Position(daily.indexFor(next), false, shownStreak);
    }

    /**
     * @param index         calendar index of today's (or the next) reward
     * @param deliveredToday whether today's reward is already in the mailbox
     * @param streak        the current streak
     */
    public record Position(int index, boolean deliveredToday, int streak) {
    }
}
