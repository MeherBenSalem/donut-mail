package io.nightbeam.donutmail.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** A reward code was redeemed and its reward delivered to the mailbox. Asynchronous. */
public final class CodeRedeemEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final String code;
    private final UUID mailId;

    public CodeRedeemEvent(UUID player, String code, UUID mailId) {
        super(true);
        this.player = player;
        this.code = code;
        this.mailId = mailId;
    }

    public UUID getPlayer() { return player; }
    public String getCode() { return code; }
    public UUID getMailId() { return mailId; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
