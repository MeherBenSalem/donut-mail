package io.nightbeam.donutmail.api;

import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One mail for every player (including players who join later), claimed once per player and stored once.
 *
 * <pre>{@code
 * DonutMailAPI.createCampaign(CampaignRequest.named("summer")
 *         .message("<gold>Summer event!</gold> Thanks for playing.")
 *         .item(new ItemStack(Material.ELYTRA))
 *         .ends(Instant.now().plus(Duration.ofDays(7)))
 *         .build());
 * }</pre>
 */
public final class CampaignRequest {
    private final String name;
    private final String sender;
    private final String message;
    private final List<ItemStack> items;
    private final double money;
    private final String voucherId;
    private final List<String> commands;
    private final Instant starts;
    private final Instant ends;
    private final String permission;

    private CampaignRequest(Builder b) {
        this.name = b.name;
        this.sender = b.sender;
        this.message = b.message;
        this.items = List.copyOf(b.items);
        this.money = b.money;
        this.voucherId = b.voucherId;
        this.commands = List.copyOf(b.commands);
        this.starts = b.starts;
        this.ends = b.ends;
        this.permission = b.permission;
    }

    /** {@code name} identifies the campaign in commands; it must be unique. */
    public static Builder named(String name) {
        return new Builder(name);
    }

    public String name() { return name; }
    public String sender() { return sender; }
    public String message() { return message; }
    public List<ItemStack> items() { return items; }
    public double money() { return money; }
    public String voucherId() { return voucherId; }
    public List<String> commands() { return commands; }
    public Instant starts() { return starts; }
    public Instant ends() { return ends; }
    public String permission() { return permission; }

    public static final class Builder {
        private final String name;
        private String sender = "Server";
        private String message = "";
        private final List<ItemStack> items = new ArrayList<>();
        private double money;
        private String voucherId;
        private final List<String> commands = new ArrayList<>();
        private Instant starts;
        private Instant ends;
        private String permission;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        public Builder sender(String sender) { this.sender = sender; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder item(ItemStack item) {
            if (item != null && !item.getType().isAir()) items.add(item.clone());
            return this;
        }
        public Builder money(double amount) { this.money = Math.max(0, amount); return this; }
        public Builder voucher(String id) { this.voucherId = id; return this; }
        public Builder command(String command) {
            if (command != null && !command.isBlank()) commands.add(command);
            return this;
        }
        /** Null = now. */
        public Builder starts(Instant time) { this.starts = time; return this; }
        /** Null = no end. */
        public Builder ends(Instant time) { this.ends = time; return this; }
        /** Only players with this permission see it. */
        public Builder permission(String node) { this.permission = node; return this; }
        public CampaignRequest build() { return new CampaignRequest(this); }
    }
}
