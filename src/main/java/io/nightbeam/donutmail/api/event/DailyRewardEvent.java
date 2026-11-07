package io.nightbeam.donutmail.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** A daily reward was delivered to a mailbox. Asynchronous. */
public final class DailyRewardEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final UUID mailId;
    private final int day;
    private final int streak;

    public DailyRewardEvent(UUID player, UUID mailId, int day, int streak) {
        super(true);
        this.player = player;
        this.mailId = mailId;
        this.day = day;
        this.streak = streak;
    }

    public UUID getPlayer() { return player; }
    public UUID getMailId() { return mailId; }
    /** The calendar day (from daily.yml). */
    public int getDay() { return day; }
    public int getStreak() { return streak; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
