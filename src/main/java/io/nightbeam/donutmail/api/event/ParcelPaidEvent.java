package io.nightbeam.donutmail.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** A cash-on-delivery parcel was paid: the money was taken and the receipt saved. Asynchronous. */
public final class ParcelPaidEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID mailId;
    private final UUID buyer;
    private final UUID seller;
    private final double price;
    private final double toSeller;

    public ParcelPaidEvent(UUID mailId, UUID buyer, UUID seller, double price, double toSeller) {
        super(true);
        this.mailId = mailId;
        this.buyer = buyer;
        this.seller = seller;
        this.price = price;
        this.toSeller = toSeller;
    }

    public UUID getMailId() { return mailId; }
    /** The recipient who paid. */
    public UUID getBuyer() { return buyer; }
    /** The sender who gets the money. */
    public UUID getSeller() { return seller; }
    public double getPrice() { return price; }
    /** What the sender receives after cash-on-delivery tax. */
    public double getToSeller() { return toSeller; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
