package io.nightbeam.donutmail.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** A parcel went back to its sender. Asynchronous. */
public final class ParcelReturnedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID mailId;
    private final UUID returnId;
    private final UUID sender;
    private final UUID receiver;
    private final Reason reason;

    public ParcelReturnedEvent(UUID mailId, UUID returnId, UUID sender, UUID receiver, Reason reason) {
        super(true);
        this.mailId = mailId;
        this.returnId = returnId;
        this.sender = sender;
        this.receiver = receiver;
        this.reason = reason;
    }

    public UUID getMailId() { return mailId; }
    /** The new mail that carries the items back. */
    public UUID getReturnId() { return returnId; }
    public UUID getSender() { return sender; }
    public UUID getReceiver() { return receiver; }
    public Reason getReason() { return reason; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }

    public enum Reason { DECLINED, EXPIRED, RECALLED, REMOVED }
}
