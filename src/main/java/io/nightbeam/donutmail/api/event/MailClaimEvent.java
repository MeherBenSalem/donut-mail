package io.nightbeam.donutmail.api.event;

import io.nightbeam.donutmail.model.MailKind;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/** After a claim was saved and handed over. Asynchronous. */
public final class MailClaimEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final UUID mailId;
    private final MailKind kind;
    private final List<ItemStack> delivered;
    private final double money;
    private final boolean partial;

    public MailClaimEvent(UUID player, UUID mailId, MailKind kind, List<ItemStack> delivered, double money, boolean partial) {
        super(true);
        this.player = player;
        this.mailId = mailId;
        this.kind = kind;
        this.delivered = delivered == null ? List.of() : delivered.stream().map(ItemStack::clone).toList();
        this.money = money;
        this.partial = partial;
    }

    public UUID getPlayer() { return player; }
    public UUID getMailId() { return mailId; }
    public MailKind getKind() { return kind; }
    /** Copies of the stacks the player received. */
    public List<ItemStack> getDelivered() { return delivered; }
    public double getMoney() { return money; }
    /** True when some stacks did not fit and stay in the mailbox. */
    public boolean isPartial() { return partial; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
