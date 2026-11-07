package io.nightbeam.donutmail.command;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.config.Messages;
import io.nightbeam.donutmail.gui.Feedback;
import io.nightbeam.donutmail.gui.MailboxGui;
import io.nightbeam.donutmail.listener.MailboxBlockListener;
import io.nightbeam.donutmail.model.BlockEntry;
import io.nightbeam.donutmail.model.Campaign;
import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.MailState;
import io.nightbeam.donutmail.model.RewardCode;
import io.nightbeam.donutmail.service.ClaimResult;
import io.nightbeam.donutmail.service.CodeService;
import io.nightbeam.donutmail.service.ParcelService;
import io.nightbeam.donutmail.service.Rewards;
import io.nightbeam.donutmail.util.Durations;
import io.nightbeam.donutmail.util.ItemSerializer;
import io.nightbeam.donutmail.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** /mail and /mailbox. */
public final class MailCommand implements CommandExecutor, TabCompleter {
    private static final List<String> PLAYER_SUBS = List.of("inbox", "parcels", "sent", "rewards", "send", "cod", "claimall", "redeem",
            "block", "unblock", "blocklist", "toggle", "help");
    private static final List<String> ADMIN_SUBS = List.of("give", "announce", "campaign", "code", "voucher", "admin", "reload", "senditem");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DonutMailPlugin plugin;

    public MailCommand(DonutMailPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages messages() {
        return plugin.messages();
    }

    /** 2.0 permission or its PostMaster-era equivalent. */
    public static boolean has(CommandSender sender, String node) {
        if (sender.hasPermission(node)) return true;
        if (node.startsWith("donutmail.")) {
            String legacy = "postmaster." + node.substring("donutmail.".length());
            if (sender.isPermissionSet(legacy) && sender.hasPermission(legacy)) return true;
        }
        return false;
    }

    private boolean admin(CommandSender sender) {
        if (has(sender, "donutmail.admin")) return true;
        messages().send(sender, "general.no-permission");
        return false;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) return player;
        messages().send(sender, "general.player-only");
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            Player player = player(sender);
            if (player == null) return true;
            if (!has(sender, "donutmail.use")) {
                messages().send(sender, "general.no-permission");
                return true;
            }
            plugin.guiManager().openMailbox(player, MailKind.Tab.INBOX);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "inbox", "parcels", "sent", "rewards" -> openTab(sender, MailKind.Tab.valueOf(sub.toUpperCase(Locale.ROOT)));
            case "send", "post", "parcel" -> send(sender, args);
            case "senditem" -> compose(sender, args.length > 1 ? args[1] : null, 0);
            case "cod" -> cod(sender, args);
            case "claimall" -> claimAll(sender);
            case "redeem" -> redeem(sender, args);
            case "block" -> block(sender, args, true);
            case "unblock" -> block(sender, args, false);
            case "blocklist" -> blockList(sender);
            case "toggle" -> toggle(sender);
            case "help" -> {
                messages().sendList(sender, "help");
                if (has(sender, "donutmail.admin")) messages().sendList(sender, "admin.help");
            }
            case "give" -> {
                if (admin(sender)) give(sender, args);
            }
            case "announce" -> {
                if (admin(sender)) announce(sender, Arrays.copyOfRange(args, 1, args.length), false, null);
            }
            case "campaign", "campaigns" -> {
                if (admin(sender)) campaign(sender, args);
            }
            case "code", "codes" -> {
                if (admin(sender)) code(sender, args);
            }
            case "voucher" -> {
                if (admin(sender)) voucher(sender, args);
            }
            case "admin" -> {
                if (admin(sender)) adminTools(sender, args);
            }
            case "reload" -> {
                if (admin(sender)) {
                    plugin.reloadRuntime();
                    messages().send(sender, "general.reloaded");
                }
            }
            default -> {
                // "/mail <player>" opens a parcel for that player.
                if (sender instanceof Player && args.length == 1) {
                    compose(sender, args[0], 0);
                } else {
                    messages().sendList(sender, "help");
                }
            }
        }
        return true;
    }

    // ── Player commands ─────────────────────────────────────────────────────

    private void openTab(CommandSender sender, MailKind.Tab tab) {
        Player player = player(sender);
        if (player == null) return;
        if (!has(sender, "donutmail.use")) {
            messages().send(sender, "general.no-permission");
            return;
        }
        plugin.guiManager().openMailbox(player, tab);
    }

    private void send(CommandSender sender, String[] args) {
        // 1.x: /mail send <targets> <message> sends text mail (staff).
        if (args.length >= 3 && !args[2].equalsIgnoreCase("cod") && has(sender, "donutmail.admin")) {
            textMail(sender, args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
            return;
        }
        if (args.length < 2) {
            if (sender instanceof Player) {
                compose(sender, null, 0);
            } else {
                messages().send(sender, "admin.usage-send");
            }
            return;
        }
        double price = 0;
        if (args.length >= 4 && args[2].equalsIgnoreCase("cod")) {
            price = Durations.parseAmount(args[3]);
            if (Double.isNaN(price) || price < plugin.settings().codMin || price > plugin.settings().codMax) {
                messages().send(sender, "parcels.price-invalid", "min", plugin.economy().format(plugin.settings().codMin),
                        "max", plugin.economy().format(plugin.settings().codMax));
                return;
            }
        } else if (args.length >= 3) {
            messages().send(sender, "parcels.usage-send");
            return;
        }
        compose(sender, args[1], price);
    }

    private void cod(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages().send(sender, "parcels.usage-send");
            return;
        }
        double price = Durations.parseAmount(args[2]);
        if (Double.isNaN(price) || price < plugin.settings().codMin || price > plugin.settings().codMax) {
            messages().send(sender, "parcels.price-invalid", "min", plugin.economy().format(plugin.settings().codMin),
                    "max", plugin.economy().format(plugin.settings().codMax));
            return;
        }
        compose(sender, args[1], price);
    }

    private void compose(CommandSender sender, String name, double price) {
        Player player = player(sender);
        if (player == null) return;
        if (!has(sender, "donutmail.senditem")) {
            messages().send(sender, "general.no-permission");
            return;
        }
        if (!plugin.settings().parcelsEnabled) {
            messages().send(sender, "parcels.disabled");
            return;
        }
        if (price > 0 && (!plugin.settings().codEnabled || !has(sender, "donutmail.cod"))) {
            messages().send(sender, plugin.settings().codEnabled ? "general.no-permission" : "parcels.cod-disabled");
            return;
        }
        if (price > 0 && !plugin.economy().available()) {
            messages().send(sender, "general.economy-missing");
            return;
        }
        if (name == null) {
            plugin.guiManager().openCompose(player, null, price);
            return;
        }
        plugin.parcels().resolve(name).thenAccept(found -> plugin.scheduler().atPlayer(player, () -> {
            if (found.isEmpty()) {
                messages().send(player, "general.unknown-player", "player", name);
                return;
            }
            if (found.get().id().equals(player.getUniqueId()) && !plugin.settings().allowSelf) {
                messages().send(player, "parcels.self");
                return;
            }
            messages().send(player, "parcels.compose-opened", "player", found.get().name());
            plugin.guiManager().openCompose(player, found.get(), price);
        }));
    }

    private void claimAll(CommandSender sender) {
        Player player = player(sender);
        if (player == null || !has(sender, "donutmail.use")) return;
        plugin.scheduler().supply(() -> plugin.storage().active(player.getUniqueId())).thenAccept(active -> {
            List<MailRecord> claimable = active.stream().filter(m -> m.state() == MailState.PENDING).toList();
            boolean started = plugin.mail().guarded(player.getUniqueId(), () -> claimEach(player, claimable, 0, 0, false), result ->
                    plugin.scheduler().atPlayer(player, () -> {
                        int count = result == null ? 0 : result[0];
                        boolean full = result != null && result[1] == 1;
                        if (count == 0 && !full) messages().send(player, "mailbox.claim-all-none");
                        else if (count == 0) Feedback.error(plugin, player, "mailbox.inventory-full");
                        else messages().send(player, full ? "mailbox.claim-all-partial" : "mailbox.claim-all", "count", String.valueOf(count));
                    }));
            if (!started) plugin.scheduler().atPlayer(player, () -> messages().send(player, "general.busy"));
        });
    }

    private CompletableFuture<int[]> claimEach(Player player, List<MailRecord> mails, int index, int claimed, boolean full) {
        if (index >= mails.size() || full) return CompletableFuture.completedFuture(new int[]{claimed, full ? 1 : 0});
        return plugin.mail().claim(player, mails.get(index).id()).thenCompose(result -> {
            boolean stop = result.status() == ClaimResult.Status.PARTIAL || result.status() == ClaimResult.Status.INVENTORY_FULL
                    || result.status() == ClaimResult.Status.OFFLINE || result.status() == ClaimResult.Status.ERROR;
            return claimEach(player, mails, index + 1, claimed + (result.success() ? 1 : 0), stop);
        });
    }

    private void redeem(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) return;
        if (!plugin.settings().codesEnabled) {
            messages().send(sender, "general.disabled");
            return;
        }
        if (!has(sender, "donutmail.redeem")) {
            messages().send(sender, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            messages().send(sender, "codes.usage");
            return;
        }
        String code = args[1];
        plugin.codes().redeem(player, code).thenAccept(outcome ->
                plugin.scheduler().atPlayer(player, () -> MailboxGui.redeemFeedback(plugin, player, outcome, code)));
    }

    private void block(CommandSender sender, String[] args, boolean add) {
        Player player = player(sender);
        if (player == null) return;
        if (args.length < 2) {
            messages().send(sender, "block.usage");
            return;
        }
        String name = args[1];
        plugin.parcels().resolve(name).thenAccept(found -> {
            if (found.isEmpty()) {
                messages().send(player, "general.unknown-player", "player", name);
                return;
            }
            ParcelService.Recipient target = found.get();
            if (target.id().equals(player.getUniqueId())) {
                messages().send(player, "block.self");
                return;
            }
            plugin.scheduler().supply(() -> add
                    ? plugin.storage().block(new BlockEntry(player.getUniqueId(), target.id(), target.name(), System.currentTimeMillis()))
                    : plugin.storage().unblock(player.getUniqueId(), target.id())).thenAccept(changed -> {
                if (add) messages().send(player, changed ? "block.blocked" : "block.already", "player", target.name());
                else messages().send(player, changed ? "block.unblocked" : "block.not-blocked", "player", target.name());
            });
        });
    }

    private void blockList(CommandSender sender) {
        Player player = player(sender);
        if (player == null) return;
        plugin.scheduler().supply(() -> plugin.storage().blocks(player.getUniqueId())).thenAccept(list -> {
            if (list.isEmpty()) {
                messages().send(player, "block.list-empty");
                return;
            }
            messages().send(player, "block.list-header", "count", String.valueOf(list.size()));
            for (BlockEntry entry : list) {
                messages().sendPlain(player, "block.list-line", "player", entry.blockedName() == null ? entry.blocked().toString() : entry.blockedName());
            }
        });
    }

    private void toggle(CommandSender sender) {
        Player player = player(sender);
        if (player == null) return;
        plugin.scheduler().supply(() -> {
            boolean accept = !plugin.storage().player(player.getUniqueId()).map(r -> r.acceptParcels()).orElse(true);
            plugin.storage().setAcceptParcels(player.getUniqueId(), accept);
            return accept;
        }).thenAccept(accept -> messages().send(player, accept ? "block.accepting" : "block.refusing"));
    }

    // ── Staff: deliveries ───────────────────────────────────────────────────

    /** What a give or announce contains. */
    private record Contents(List<String> items, double money, String voucher) {
        boolean empty() {
            return items.isEmpty() && money <= 0 && voucher == null;
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages().send(sender, "admin.give-usage");
            return;
        }
        String target = args[1];
        String what = args[2].toLowerCase(Locale.ROOT);
        int next = 3;
        Contents contents;
        switch (what) {
            case "hand" -> {
                if (!(sender instanceof Player player)) {
                    messages().send(sender, "general.player-only");
                    return;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    messages().send(sender, "admin.hand-empty");
                    return;
                }
                contents = new Contents(List.of(ItemSerializer.serialize(hand.clone())), 0, null);
            }
            case "item" -> {
                if (args.length < 4) {
                    messages().send(sender, "admin.give-usage");
                    return;
                }
                Material material = Material.matchMaterial(args[3].toUpperCase(Locale.ROOT));
                if (material == null || material.isAir() || !material.isItem()) {
                    messages().send(sender, "admin.unknown-material", "material", args[3]);
                    return;
                }
                int amount = 1;
                next = 4;
                if (args.length > 4) {
                    try {
                        amount = Math.max(1, Integer.parseInt(args[4]));
                        next = 5;
                    } catch (NumberFormatException notAmount) {
                        // The rest is the message.
                    }
                }
                contents = new Contents(ItemSerializer.serializeAll(Rewards.stacks(material, amount)), 0, null);
            }
            case "voucher" -> {
                if (args.length < 4 || !plugin.vouchers().exists(args[3])) {
                    messages().send(sender, "admin.unknown-voucher", "voucher", args.length < 4 ? "?" : args[3]);
                    return;
                }
                contents = new Contents(List.of(), 0, plugin.vouchers().getVoucher(args[3]).id());
                next = 4;
            }
            case "money" -> {
                double amount = args.length < 4 ? Double.NaN : Durations.parseAmount(args[3]);
                if (Double.isNaN(amount) || amount <= 0) {
                    messages().send(sender, "admin.invalid-amount", "value", args.length < 4 ? "" : args[3]);
                    return;
                }
                if (!plugin.economy().available()) {
                    messages().send(sender, "general.economy-missing");
                    return;
                }
                contents = new Contents(List.of(), amount, null);
                next = 4;
            }
            default -> {
                messages().send(sender, "admin.give-usage");
                return;
            }
        }
        String message = args.length > next ? String.join(" ", Arrays.copyOfRange(args, next, args.length))
                : contents.voucher() != null ? messages().format("mail.voucher", "voucher", contents.voucher())
                : messages().format("mail.give", "sender", sender.getName());
        deliverTo(sender, target, message, contents);
    }

    /** Delivers to a player, {@code online}, {@code all} (one campaign for every known player) or {@code a;b;c}. */
    private void deliverTo(CommandSender sender, String target, String message, Contents contents) {
        String token = target.toLowerCase(Locale.ROOT);
        if (token.equals("all")) {
            long now = System.currentTimeMillis();
            String name = "give-" + Long.toString(now, 36);
            Campaign campaign = new Campaign(UUID.randomUUID(), name, sender.getName(), message, contents.items(), contents.money(),
                    contents.voucher(), List.of(), 0, 0, now, sender.getName(), 0, null, true);
            plugin.campaigns().create(campaign).thenAccept(ok -> messages().send(sender, ok ? "admin.give-all" : "admin.campaign-exists",
                    "name", name));
            return;
        }
        if (token.equals("online") || token.equals("allonline")) {
            List<MailRecord> mails = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                mails.add(delivery(sender, online.getUniqueId(), online.getName(), message, contents));
            }
            plugin.mail().deliverAll(mails).thenAccept(saved -> messages().send(sender, "admin.sent", "count", String.valueOf(saved.size())));
            return;
        }
        List<String> names = new ArrayList<>(new LinkedHashSet<>(Arrays.asList(target.split(";"))));
        List<CompletableFuture<Optional<ParcelService.Recipient>>> lookups = new ArrayList<>();
        for (String name : names) lookups.add(resolveTarget(name.trim()));
        CompletableFuture.allOf(lookups.toArray(new CompletableFuture[0])).thenAccept(ignored -> {
            List<MailRecord> mails = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                Optional<ParcelService.Recipient> found = lookups.get(i).join();
                if (found.isEmpty()) {
                    messages().send(sender, "general.unknown-player", "player", names.get(i));
                } else {
                    mails.add(delivery(sender, found.get().id(), found.get().name(), message, contents));
                }
            }
            if (mails.isEmpty()) return;
            plugin.mail().deliverAll(mails).thenAccept(saved -> messages().send(sender, "admin.sent", "count", String.valueOf(saved.size())));
        });
    }

    private CompletableFuture<Optional<ParcelService.Recipient>> resolveTarget(String name) {
        try {
            UUID id = UUID.fromString(name);
            return CompletableFuture.completedFuture(Optional.of(new ParcelService.Recipient(id, name)));
        } catch (IllegalArgumentException notUuid) {
            return plugin.parcels().resolve(name);
        }
    }

    private MailRecord delivery(CommandSender sender, UUID id, String name, String message, Contents contents) {
        MailKind kind = contents.empty() ? MailKind.MESSAGE : MailKind.DELIVERY;
        return plugin.mail().serverMail(kind, id, sender.getName()).receiverName(name).message(message).items(contents.items())
                .money(contents.money()).voucherId(contents.voucher()).build();
    }

    private void textMail(CommandSender sender, String targets, String message) {
        if (targets.equalsIgnoreCase("all")) {
            announce(sender, new String[]{message}, true, "message-" + Long.toString(System.currentTimeMillis(), 36));
            return;
        }
        deliverTo(sender, targets, message, new Contents(List.of(), 0, null));
    }

    private void voucher(CommandSender sender, String[] args) {
        if (args.length >= 3 && args[1].equalsIgnoreCase("sendall")) {
            sendVoucher(sender, args[2], "all");
        } else if (args.length >= 4 && args[1].equalsIgnoreCase("send")) {
            sendVoucher(sender, args[2], args[3]);
        } else {
            messages().send(sender, "admin.usage-voucher");
        }
    }

    private void sendVoucher(CommandSender sender, String id, String targets) {
        if (!plugin.vouchers().exists(id)) {
            messages().send(sender, "admin.unknown-voucher", "voucher", id);
            return;
        }
        String voucher = plugin.vouchers().getVoucher(id).id();
        deliverTo(sender, targets, messages().format("mail.voucher", "voucher", voucher), new Contents(List.of(), 0, voucher));
    }

    // ── Staff: campaigns ────────────────────────────────────────────────────

    /** Parses "key:value" options at the start; the rest is the message. */
    private static Map<String, String> options(String[] tokens, List<String> rest, Set<String> flags) {
        Map<String, String> out = new HashMap<>();
        boolean message = false;
        for (String token : tokens) {
            if (!message) {
                int colon = token.indexOf(':');
                String key = colon > 0 ? token.substring(0, colon).toLowerCase(Locale.ROOT) : "";
                if (colon > 0 && Set.of("money", "voucher", "start", "end", "name", "permission", "perm", "uses", "per-player",
                        "expires", "sender").contains(key)) {
                    out.put(key.equals("perm") ? "permission" : key, token.substring(colon + 1));
                    continue;
                }
                if (token.equalsIgnoreCase("hand")) {
                    flags.add("hand");
                    continue;
                }
                message = true;
            }
            rest.add(token);
        }
        return out;
    }

    private void announce(CommandSender sender, String[] tokens, boolean existingOnly, String forcedName) {
        List<String> rest = new ArrayList<>();
        Set<String> flags = new java.util.HashSet<>();
        Map<String, String> options = options(tokens, rest, flags);
        if (rest.isEmpty()) {
            messages().send(sender, "admin.announce-usage");
            return;
        }
        Contents contents = contents(sender, options, flags);
        if (contents == null) return;
        long now = System.currentTimeMillis();
        ZoneId zone = plugin.daily().zone;
        long start = options.containsKey("start") ? Durations.parseMoment(options.get("start"), now, zone) : 0;
        long end = options.containsKey("end") ? Durations.parseMoment(options.get("end"), now, zone) : 0;
        if (start < 0 || end < 0 || (end > 0 && end <= Math.max(now, start))) {
            messages().send(sender, "admin.announce-usage");
            return;
        }
        String name = forcedName != null ? forcedName : options.getOrDefault("name", "campaign-" + Long.toString(now, 36));
        String senderName = options.getOrDefault("sender", sender instanceof Player ? sender.getName() : "Server");
        Campaign campaign = new Campaign(UUID.randomUUID(), name, senderName, String.join(" ", rest), contents.items(), contents.money(),
                contents.voucher(), List.of(), start, end, now, sender.getName(), 0, options.get("permission"), existingOnly);
        plugin.campaigns().create(campaign).thenAccept(ok -> {
            if (!ok) {
                messages().send(sender, "admin.campaign-exists", "name", name);
                return;
            }
            String window = messages().format("admin.campaign-window", "start", Text.trusted(start == 0 ? messages().raw("admin.campaign-now")
                    : date(start)), "end", Text.trusted(end == 0 ? messages().raw("admin.campaign-forever") : date(end)));
            messages().send(sender, "admin.campaign-created", "name", name, "window", Text.trusted(window));
        });
    }

    private Contents contents(CommandSender sender, Map<String, String> options, Set<String> flags) {
        List<String> items = new ArrayList<>();
        if (flags.contains("hand")) {
            if (!(sender instanceof Player player)) {
                messages().send(sender, "general.player-only");
                return null;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                messages().send(sender, "admin.hand-empty");
                return null;
            }
            items.add(ItemSerializer.serialize(hand.clone()));
        }
        double money = 0;
        if (options.containsKey("money")) {
            money = Durations.parseAmount(options.get("money"));
            if (Double.isNaN(money) || money < 0) {
                messages().send(sender, "admin.invalid-amount", "value", options.get("money"));
                return null;
            }
        }
        String voucher = options.get("voucher");
        if (voucher != null) {
            if (!plugin.vouchers().exists(voucher)) {
                messages().send(sender, "admin.unknown-voucher", "voucher", voucher);
                return null;
            }
            voucher = plugin.vouchers().getVoucher(voucher).id();
        }
        return new Contents(items, money, voucher);
    }

    private String date(long millis) {
        return DATE.withZone(plugin.daily().zone).format(Instant.ofEpochMilli(millis));
    }

    private void campaign(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        if (action.equals("list")) {
            plugin.scheduler().supply(() -> {
                List<String[]> rows = new ArrayList<>();
                for (Campaign campaign : plugin.storage().campaigns()) {
                    rows.add(new String[]{campaign.name(), state(campaign), String.valueOf(plugin.storage().campaignClaims(campaign.id())),
                            window(campaign)});
                }
                return rows;
            }).thenAccept(rows -> {
                messages().send(sender, "admin.campaign-list-header", "count", String.valueOf(rows.size()));
                for (String[] row : rows) {
                    messages().sendPlain(sender, "admin.campaign-list-line", "name", row[0], "state", Text.trusted(row[1]), "claims", row[2],
                            "window", Text.trusted(row[3]));
                }
            });
            return;
        }
        if (args.length < 3) {
            messages().send(sender, "admin.announce-usage");
            return;
        }
        String name = args[2];
        plugin.scheduler().supply(() -> plugin.storage().campaign(name)).thenAccept(found -> {
            if (found.isEmpty()) {
                messages().send(sender, "admin.campaign-unknown", "name", name);
                return;
            }
            Campaign campaign = found.get();
            switch (action) {
                case "end" -> plugin.scheduler().supply(() -> plugin.storage().endCampaign(campaign.id(), System.currentTimeMillis()))
                        .thenAccept(ok -> {
                            plugin.campaigns().reloadAsync();
                            plugin.audit().log("CAMPAIGN_END", "name", campaign.name(), "by", sender.getName());
                            messages().send(sender, "admin.campaign-ended", "name", campaign.name());
                        });
                case "delete" -> plugin.scheduler().supply(() -> plugin.storage().deleteCampaign(campaign.id())).thenAccept(ok -> {
                    plugin.campaigns().reloadAsync();
                    plugin.audit().log("CAMPAIGN_DELETE", "name", campaign.name(), "by", sender.getName());
                    messages().send(sender, "admin.campaign-deleted", "name", campaign.name());
                });
                case "addcommand" -> {
                    if (args.length < 4) {
                        messages().send(sender, "admin.announce-usage");
                        return;
                    }
                    List<String> commands = new ArrayList<>(campaign.commands());
                    commands.add(String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                    plugin.scheduler().supply(() -> plugin.storage().updateCampaignCommands(campaign.id(), commands)).thenAccept(ok -> {
                        plugin.campaigns().reloadAsync();
                        messages().send(sender, "admin.campaign-command-added", "name", campaign.name());
                    });
                }
                default -> messages().send(sender, "admin.announce-usage");
            }
        });
    }

    private String state(Campaign campaign) {
        long now = System.currentTimeMillis();
        if (campaign.isOver(now)) return messages().raw("admin.campaign-state-over");
        if (campaign.isLive(now)) return messages().raw("admin.campaign-state-live");
        return messages().raw("admin.campaign-state-scheduled");
    }

    private String window(Campaign campaign) {
        return (campaign.startsAt() == 0 ? messages().raw("admin.campaign-now") : date(campaign.startsAt())) + " &7- "
                + (campaign.endsAt() == 0 ? messages().raw("admin.campaign-forever") : date(campaign.endsAt()));
    }

    // ── Staff: codes ────────────────────────────────────────────────────────

    private void code(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "list" -> plugin.scheduler().supply(() -> plugin.storage().codes()).thenAccept(codes -> {
                messages().send(sender, "admin.code-list-header", "count", String.valueOf(codes.size()));
                for (RewardCode code : codes) {
                    messages().sendPlain(sender, "admin.code-list-line", "code", code.code(), "uses", String.valueOf(code.uses()),
                            "max", code.maxUses() == 0 ? messages().raw("admin.unlimited") : String.valueOf(code.maxUses()),
                            "per", code.perPlayer() == 0 ? messages().raw("admin.unlimited") : String.valueOf(code.perPlayer()),
                            "expires", Text.trusted(code.expiresAt() == 0 ? "" : messages().format("admin.code-expires", "when", date(code.expiresAt()))));
                }
            });
            case "create" -> createCode(sender, args);
            case "delete" -> {
                if (args.length < 3) {
                    messages().send(sender, "admin.code-usage");
                    return;
                }
                plugin.scheduler().supply(() -> plugin.storage().deleteCode(args[2])).thenAccept(ok -> {
                    if (ok) plugin.audit().log("CODE_DELETE", "code", RewardCode.normalize(args[2]), "by", sender.getName());
                    messages().send(sender, ok ? "admin.code-deleted" : "admin.code-unknown", "code", RewardCode.normalize(args[2]));
                });
            }
            case "addcommand" -> {
                if (args.length < 4) {
                    messages().send(sender, "admin.code-usage");
                    return;
                }
                String command = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                plugin.scheduler().supply(() -> plugin.storage().code(args[2]).map(code -> {
                    List<String> commands = new ArrayList<>(code.commands());
                    commands.add(command);
                    return plugin.storage().updateCodeCommands(code.code(), commands);
                }).orElse(false)).thenAccept(ok -> messages().send(sender, ok ? "admin.code-command-added" : "admin.code-unknown",
                        "code", RewardCode.normalize(args[2])));
            }
            default -> messages().send(sender, "admin.code-usage");
        }
    }

    private void createCode(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages().send(sender, "admin.code-usage");
            return;
        }
        String code = args[2];
        if (!CodeService.VALID.matcher(code).matches()) {
            messages().send(sender, "admin.code-invalid");
            return;
        }
        List<String> rest = new ArrayList<>();
        Set<String> flags = new java.util.HashSet<>();
        Map<String, String> options = options(Arrays.copyOfRange(args, 3, args.length), rest, flags);
        Contents contents = contents(sender, options, flags);
        if (contents == null) return;
        int uses;
        int perPlayer;
        try {
            uses = Math.max(0, Integer.parseInt(options.getOrDefault("uses", "0")));
            perPlayer = Math.max(0, Integer.parseInt(options.getOrDefault("per-player", "1")));
        } catch (NumberFormatException invalid) {
            messages().send(sender, "admin.code-usage");
            return;
        }
        long now = System.currentTimeMillis();
        long expires = options.containsKey("expires") ? Durations.parseMoment(options.get("expires"), now, plugin.daily().zone) : 0;
        if (expires < 0) {
            messages().send(sender, "admin.code-usage");
            return;
        }
        RewardCode reward = new RewardCode(code, String.join(" ", rest), contents.items(), contents.money(), contents.voucher(), List.of(),
                uses, 0, perPlayer, expires, now, sender.getName(), true);
        plugin.scheduler().supply(() -> plugin.storage().insertCode(reward)).thenAccept(ok -> {
            if (!ok) {
                messages().send(sender, "admin.code-exists", "code", reward.code());
                return;
            }
            plugin.audit().log("CODE_CREATE", "code", reward.code(), "by", sender.getName(), "uses", uses, "perPlayer", perPlayer,
                    "expires", expires, "items", reward.items().size(), "money", reward.money(), "voucher", reward.voucherId());
            messages().send(sender, "admin.code-created", "code", reward.code(),
                    "uses", uses == 0 ? messages().raw("admin.unlimited") : String.valueOf(uses),
                    "per", perPlayer == 0 ? messages().raw("admin.unlimited") : String.valueOf(perPlayer),
                    "expires", Text.trusted(expires == 0 ? "" : messages().format("admin.code-expires", "when", date(expires))));
        });
    }

    // ── Staff: tools ────────────────────────────────────────────────────────

    private void adminTools(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "view" -> {
                Player staff = player(sender);
                if (staff == null) return;
                if (args.length < 3) {
                    messages().send(sender, "admin.view-usage");
                    return;
                }
                resolveTarget(args[2]).thenAccept(found -> {
                    if (found.isEmpty()) {
                        messages().send(sender, "general.unknown-player", "player", args[2]);
                        return;
                    }
                    plugin.guiManager().openAdmin(staff, found.get().id(), found.get().name(), 0);
                });
            }
            case "info" -> info(sender);
            case "purge" -> {
                int days;
                try {
                    days = Integer.parseInt(args.length > 2 ? args[2] : "x");
                } catch (NumberFormatException invalid) {
                    messages().send(sender, "admin.purge-usage");
                    return;
                }
                long before = System.currentTimeMillis() - Math.max(0, days) * 86_400_000L;
                plugin.scheduler().supply(() -> plugin.storage().purge(before)).thenAccept(count -> {
                    plugin.audit().log("PURGE", "by", sender.getName(), "days", days, "rows", count);
                    messages().send(sender, "admin.purged", "count", String.valueOf(count), "days", String.valueOf(days));
                });
            }
            case "setblock", "removeblock" -> mailboxBlock(sender, action.equals("setblock"));
            default -> messages().sendList(sender, "admin.help");
        }
    }

    private void info(CommandSender sender) {
        plugin.scheduler().supply(() -> plugin.storage().stats()).thenAccept(stats -> {
            messages().send(sender, "admin.info-header", "version", plugin.getPluginMeta().getVersion());
            messages().sendPlain(sender, "admin.info-platform", "platform", Bukkit.getName() + " " + Bukkit.getVersion(),
                    "folia", plugin.scheduler().isFolia() ? "Folia" : "Paper");
            messages().sendPlain(sender, "admin.info-storage", "storage", plugin.storage().type().name(), "server", plugin.serverId());
            messages().sendPlain(sender, "admin.info-hooks", "economy", plugin.economy().name(),
                    "papi", String.valueOf(plugin.placeholdersHooked()), "votifier", String.valueOf(plugin.votesHooked()));
            messages().sendPlain(sender, "admin.info-counts", "mail", str(stats, "mail"), "active", str(stats, "active"),
                    "cod", str(stats, "cod"), "claiming", str(stats, "claiming"));
            messages().sendPlain(sender, "admin.info-other", "campaigns", str(stats, "campaigns"), "codes", str(stats, "codes"),
                    "players", str(stats, "players"));
            messages().sendPlain(sender, "admin.info-network", "network",
                    plugin.network().enabled() ? "every " + plugin.settings().pollSeconds + "s" : "off (single server)");
        });
    }

    private static String str(Map<String, Long> stats, String key) {
        return String.valueOf(stats.getOrDefault(key, 0L));
    }

    private void mailboxBlock(CommandSender sender, boolean add) {
        Player player = player(sender);
        if (player == null) return;
        plugin.scheduler().atPlayer(player, () -> {
            Block block = player.getTargetBlockExact(5);
            if (block == null || block.getType().isAir()) {
                messages().send(player, "admin.block-none");
                return;
            }
            String key = MailboxBlockListener.key(block.getLocation());
            List<String> locations = new ArrayList<>(plugin.settings().mailboxLocations);
            if (add) {
                if (!locations.contains(key)) locations.add(key);
                messages().send(player, "admin.block-added");
            } else {
                if (!locations.remove(key)) {
                    messages().send(player, "admin.block-not-mailbox");
                    return;
                }
                messages().send(player, "admin.block-removed");
            }
            plugin.saveMailboxBlocks(locations);
        });
    }

    // ── Tab completion ──────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean staff = has(sender, "donutmail.admin");
        if (args.length == 1) {
            List<String> options = new ArrayList<>(PLAYER_SUBS);
            if (staff) options.addAll(ADMIN_SUBS);
            return filter(args[0], options);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "send", "post", "parcel", "senditem", "cod", "block", "unblock" -> {
                    return filter(args[1], playerNames(sender));
                }
                case "give" -> {
                    List<String> names = new ArrayList<>(List.of("online", "all"));
                    names.addAll(playerNames(sender));
                    return filter(args[1], names);
                }
                case "campaign", "campaigns" -> {
                    return filter(args[1], List.of("list", "end", "delete", "addcommand"));
                }
                case "code", "codes" -> {
                    return filter(args[1], List.of("create", "delete", "list", "addcommand"));
                }
                case "voucher" -> {
                    return filter(args[1], List.of("send", "sendall"));
                }
                case "admin" -> {
                    return filter(args[1], List.of("view", "info", "purge", "setblock", "removeblock"));
                }
                case "announce" -> {
                    return filter(args[1], List.of("hand", "money:", "voucher:", "start:", "end:", "name:", "permission:"));
                }
                default -> {
                    return List.of();
                }
            }
        }
        if (args.length == 3) {
            switch (sub) {
                case "send" -> {
                    return filter(args[2], List.of("cod"));
                }
                case "give" -> {
                    return filter(args[2], List.of("hand", "item", "voucher", "money"));
                }
                case "admin" -> {
                    if (args[1].equalsIgnoreCase("view")) return filter(args[2], playerNames(sender));
                    if (args[1].equalsIgnoreCase("purge")) return filter(args[2], List.of("30", "60", "90"));
                    return List.of();
                }
                case "voucher" -> {
                    return filter(args[2], plugin.vouchers().all().stream().map(v -> v.id()).toList());
                }
                case "campaign", "campaigns" -> {
                    return filter(args[2], plugin.campaigns().all().stream().map(Campaign::name).toList());
                }
                case "code", "codes" -> {
                    if (args[1].equalsIgnoreCase("create")) return List.of();
                    return List.of();
                }
                default -> {
                    return List.of();
                }
            }
        }
        if (args.length == 4) {
            if (sub.equals("give") && args[2].equalsIgnoreCase("voucher")) {
                return filter(args[3], plugin.vouchers().all().stream().map(v -> v.id()).toList());
            }
            if (sub.equals("give") && args[2].equalsIgnoreCase("item")) {
                String prefix = args[3].toUpperCase(Locale.ROOT);
                List<String> materials = new ArrayList<>();
                for (Material material : Material.values()) {
                    if (material.isItem() && !material.isLegacy() && material.name().startsWith(prefix)) materials.add(material.name());
                    if (materials.size() >= 50) break;
                }
                return materials;
            }
            if (sub.equals("voucher") && args[1].equalsIgnoreCase("send")) {
                List<String> names = new ArrayList<>(List.of("all", "allonline"));
                names.addAll(playerNames(sender));
                return filter(args[3], names);
            }
            if (sub.equals("code") && args[1].equalsIgnoreCase("create")) {
                return filter(args[3], List.of("uses:", "per-player:", "expires:", "money:", "voucher:", "hand"));
            }
        }
        if (args.length >= 4 && sub.equals("code") && args[1].equalsIgnoreCase("create")) {
            return filter(args[args.length - 1], List.of("uses:", "per-player:", "expires:", "money:", "voucher:", "hand"));
        }
        return List.of();
    }

    /** Online players (that the sender can see) and players known to DonutMail, for offline mail. */
    private List<String> playerNames(CommandSender sender) {
        Set<String> names = new LinkedHashSet<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!(sender instanceof Player viewer) || viewer.canSee(online)) names.add(online.getName());
        }
        names.addAll(plugin.knownNames());
        return new ArrayList<>(names);
    }

    private static List<String> filter(String input, List<String> values) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).startsWith(lower)) out.add(value);
            if (out.size() >= 100) break;
        }
        return out;
    }
}
