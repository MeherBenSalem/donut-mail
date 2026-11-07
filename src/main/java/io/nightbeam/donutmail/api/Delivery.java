package io.nightbeam.donutmail.api;

import io.nightbeam.donutmail.model.MailKind;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A mail from your plugin to one player, online or offline.
 *
 * <pre>{@code
 * DonutMailAPI.deliver(Delivery.to(playerId)
 *         .sender("Crates")
 *         .message("<gold>Your crate reward")
 *         .item(new ItemStack(Material.DIAMOND, 3))
 *         .money(250)
 *         .command("lp user %player% permission set kit.vip")
 *         .rewardsTab()
 *         .build());
 * }</pre>
 */
public final class Delivery {
    private final UUID receiver;
    private final String sender;
    private final String message;
    private final List<ItemStack> items;
    private final double money;
    private final String voucherId;
    private final List<String> commands;
    private final Duration expiresIn;
    private final MailKind kind;

    private Delivery(Builder builder) {
        this.receiver = builder.receiver;
        this.sender = builder.sender;
        this.message = builder.message;
        this.items = List.copyOf(builder.items);
        this.money = builder.money;
        this.voucherId = builder.voucherId;
        this.commands = List.copyOf(builder.commands);
        this.expiresIn = builder.expiresIn;
        this.kind = builder.kind;
    }

    public static Builder to(UUID receiver) {
        return new Builder(receiver);
    }

    public UUID receiver() { return receiver; }
    public String sender() { return sender; }
    public String message() { return message; }
    public List<ItemStack> items() { return items; }
    public double money() { return money; }
    public String voucherId() { return voucherId; }
    public List<String> commands() { return commands; }
    /** Null = the server's {@code mail.expire-days}; {@link Duration#ZERO} = never. */
    public Duration expiresIn() { return expiresIn; }
    public MailKind kind() { return kind; }

    public static final class Builder {
        private final UUID receiver;
        private String sender = "Server";
        private String message = "";
        private final List<ItemStack> items = new ArrayList<>();
        private double money;
        private String voucherId;
        private final List<String> commands = new ArrayList<>();
        private Duration expiresIn;
        private MailKind kind = MailKind.DELIVERY;

        private Builder(UUID receiver) {
            this.receiver = Objects.requireNonNull(receiver, "receiver");
        }

        /** Shown as "from" (plain text). */
        public Builder sender(String sender) { this.sender = sender; return this; }
        /** MiniMessage or &-codes. */
        public Builder message(String message) { this.message = message; return this; }
        /** Copied when added. */
        public Builder item(ItemStack item) {
            if (item != null && !item.getType().isAir()) items.add(item.clone());
            return this;
        }
        public Builder items(List<ItemStack> list) {
            if (list != null) list.forEach(this::item);
            return this;
        }
        /** Paid through Vault when claimed. */
        public Builder money(double amount) { this.money = Math.max(0, amount); return this; }
        /** A voucher from plugins/DonutMail/vouchers/. */
        public Builder voucher(String id) { this.voucherId = id; return this; }
        /** Run by the console when claimed; %player% and %uuid% are replaced. */
        public Builder command(String command) {
            if (command != null && !command.isBlank()) commands.add(command);
            return this;
        }
        public Builder expiresIn(Duration duration) { this.expiresIn = duration; return this; }
        /** Lists it on the Rewards tab instead of the Inbox. */
        public Builder rewardsTab() { this.kind = MailKind.REWARD; return this; }
        public Delivery build() { return new Delivery(this); }
    }
}
