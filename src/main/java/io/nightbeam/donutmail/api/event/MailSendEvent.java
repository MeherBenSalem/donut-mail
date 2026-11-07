package io.nightbeam.donutmail.api.event;

import io.nightbeam.donutmail.model.MailKind;
import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Before a parcel or a delivery is saved. Cancel it to stop the mail; a player's parcel items are then
 * returned to them. Fired on the sender's thread for player parcels, otherwise on the calling thread
 * ({@link #isAsynchronous()} tells which).
 */
public final class MailSendEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final MailKind kind;
    private final UUID receiver;
    private final UUID senderId;
    private final String senderName;
    private final List<ItemStack> items;
    private final double money;
    private final double codPrice;
    private final String message;
    private boolean cancelled;
    private String cancelMessage;

    public MailSendEvent(MailKind kind, UUID receiver, UUID senderId, String senderName, List<ItemStack> items, double money,
                         double codPrice, String message) {
        super(!Bukkit.isPrimaryThread());
        this.kind = kind;
        this.receiver = receiver;
        this.senderId = senderId;
        this.senderName = senderName;
        this.items = items == null ? List.of() : items.stream().map(ItemStack::clone).toList();
        this.money = money;
        this.codPrice = codPrice;
        this.message = message;
    }

    public MailKind getKind() { return kind; }
    public UUID getReceiver() { return receiver; }
    /** The sending player, or null for the server, the console and other plugins. */
    public UUID getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    /** Copies of the items; changing them has no effect. */
    public List<ItemStack> getItems() { return items; }
    public double getMoney() { return money; }
    /** Cash-on-delivery price, 0 for none. */
    public double getCodPrice() { return codPrice; }
    public String getMessage() { return message; }

    public String getCancelMessage() { return cancelMessage; }

    /** Shown to the sending player when the event is cancelled (MiniMessage). */
    public void setCancelMessage(String cancelMessage) { this.cancelMessage = cancelMessage; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
