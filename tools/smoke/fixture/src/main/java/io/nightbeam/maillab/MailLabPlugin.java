package io.nightbeam.maillab;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.api.Delivery;
import io.nightbeam.donutmail.api.DonutMailAPI;
import io.nightbeam.donutmail.api.event.CodeRedeemEvent;
import io.nightbeam.donutmail.api.event.DailyRewardEvent;
import io.nightbeam.donutmail.api.event.MailClaimEvent;
import io.nightbeam.donutmail.api.event.MailSendEvent;
import io.nightbeam.donutmail.api.event.ParcelPaidEvent;
import io.nightbeam.donutmail.api.event.ParcelReturnedEvent;
import io.nightbeam.donutmail.model.MailRecord;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Test-only fixture. Registers an in-memory Vault economy when EssentialsX is not installed, counts
 * DonutMail's API events and gives the live test console helpers. Every answer is a "[LAB]" log line.
 */
public final class MailLabPlugin extends JavaPlugin implements Listener {
    private final Map<String, AtomicInteger> events = new ConcurrentHashMap<>();

    @Override
    public void onLoad() {
        if (Bukkit.getPluginManager().getPlugin("Essentials") == null) {
            Bukkit.getServicesManager().register(Economy.class, new LabEconomy(), this, ServicePriority.Highest);
        }
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        report("fixture enabled on " + Bukkit.getName() + " " + Bukkit.getBukkitVersion());
    }

    private void report(String line) {
        getLogger().info("[LAB] " + line);
    }

    private void count(String name) {
        events.computeIfAbsent(name, ignored -> new AtomicInteger()).incrementAndGet();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSend(MailSendEvent event) {
        count("send");
    }

    @EventHandler
    public void onClaim(MailClaimEvent event) {
        count("claim");
    }

    @EventHandler
    public void onPaid(ParcelPaidEvent event) {
        count("paid");
        report("event paid price=" + event.getPrice());
    }

    @EventHandler
    public void onReturned(ParcelReturnedEvent event) {
        count("returned-" + event.getReason().name().toLowerCase(Locale.ROOT));
    }

    @EventHandler
    public void onDaily(DailyRewardEvent event) {
        count("daily");
    }

    @EventHandler
    public void onCode(CodeRedeemEvent event) {
        count("code");
    }

    /** Logs block clicks, so a mailbox-block failure shows whether the click reached the server. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            report("interact " + event.getPlayer().getName() + " " + event.getClickedBlock().getType() + " hand=" + event.getHand());
        }
    }

    private static Economy economy() {
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : provider.getProvider();
    }

    private static DonutMailPlugin donutMail() {
        return (DonutMailPlugin) Bukkit.getPluginManager().getPlugin("DonutMail");
    }

    private static Player online(String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) throw new IllegalArgumentException(name + " is not online");
        return player;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "money" -> {
                    Economy economy = economy();
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    double amount = Double.parseDouble(args[2]);
                    Bukkit.getGlobalRegionScheduler().execute(this, () -> {
                        economy.createPlayerAccount(target);
                        economy.withdrawPlayer(target, economy.getBalance(target));
                        economy.depositPlayer(target, amount);
                        report("money " + args[1] + " " + economy.getBalance(target));
                    });
                }
                case "balance" -> {
                    Economy economy = economy();
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    Bukkit.getGlobalRegionScheduler().execute(this, () -> report("balance " + args[1] + " " + economy.getBalance(target)
                            + " via " + economy.getName()));
                }
                case "give" -> {
                    Player player = online(args[1]);
                    Material material = Material.matchMaterial(args[2]);
                    int amount = Integer.parseInt(args[3]);
                    player.getScheduler().run(this, task -> {
                        player.getInventory().addItem(new ItemStack(material, amount));
                        report("give " + args[1] + " " + material + " " + amount);
                    }, null);
                }
                case "fill" -> {
                    // lab fill <player> <material> <free slots>: fills the storage slots, leaving some empty.
                    Player player = online(args[1]);
                    Material material = Material.matchMaterial(args[2]);
                    int free = Integer.parseInt(args[3]);
                    player.getScheduler().run(this, task -> {
                        ItemStack[] contents = new ItemStack[36];
                        for (int i = 0; i < 36 - free; i++) contents[i] = new ItemStack(material, material.getMaxStackSize());
                        player.getInventory().setStorageContents(contents);
                        report("fill " + args[1] + " " + free);
                    }, null);
                }
                case "clear" -> {
                    Player player = online(args[1]);
                    player.getScheduler().run(this, task -> {
                        player.getInventory().clear();
                        report("clear " + args[1]);
                    }, null);
                }
                case "count" -> {
                    Player player = online(args[1]);
                    Material material = Material.matchMaterial(args[2]);
                    player.getScheduler().run(this, task -> {
                        int total = 0;
                        for (ItemStack stack : player.getInventory().getContents()) {
                            if (stack != null && stack.getType() == material) total += stack.getAmount();
                        }
                        report("count " + args[1] + " " + material + " " + total);
                    }, null);
                }
                case "deliver" -> {
                    // lab deliver <player> <material> <stacks> <amount each>: one mail with several stacks, through the API.
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    Material material = Material.matchMaterial(args[2]);
                    int stacks = Integer.parseInt(args[3]);
                    int amount = Integer.parseInt(args[4]);
                    Delivery.Builder delivery = Delivery.to(target.getUniqueId()).sender("MailLab").message("<gold>Test delivery");
                    for (int i = 0; i < stacks; i++) delivery.item(new ItemStack(material, amount));
                    DonutMailAPI.deliver(delivery.build()).whenComplete((id, failure) ->
                            report(failure == null ? "deliver " + id : "error deliver -> " + failure));
                }
                case "parcel" -> {
                    // lab parcel <from> <to> <material> <amount> <cod price>: a player parcel through the API.
                    OfflinePlayer from = Bukkit.getOfflinePlayer(args[1]);
                    OfflinePlayer to = Bukkit.getOfflinePlayer(args[2]);
                    ItemStack item = new ItemStack(Material.matchMaterial(args[3]), Integer.parseInt(args[4]));
                    DonutMailAPI.sendParcel(from.getUniqueId(), args[1], to.getUniqueId(), List.of(item), "via API", Double.parseDouble(args[5]))
                            .whenComplete((result, failure) -> report(failure == null ? "parcel " + result.status() + " " + result.mailId()
                                    : "error parcel -> " + failure));
                }
                case "mails" -> {
                    // lab mails <player>: active mail by state and kind, from storage.
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    DonutMailPlugin plugin = donutMail();
                    plugin.scheduler().supply(() -> plugin.storage().active(target.getUniqueId())).whenComplete((list, failure) -> {
                        if (failure != null) {
                            report("error mails -> " + failure);
                            return;
                        }
                        Map<String, Integer> counts = new TreeMap<>();
                        for (MailRecord mail : list) counts.merge(mail.state() + ":" + mail.kind(), 1, Integer::sum);
                        report("mails " + args[1] + " " + (counts.isEmpty() ? "none" : counts.entrySet().stream()
                                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(","))));
                    });
                }
                case "api" -> report("api available=" + DonutMailAPI.isAvailable());
                case "waiting" -> {
                    Player player = online(args[1]);
                    report("waiting " + args[1] + " " + DonutMailAPI.waiting(player.getUniqueId()) + " unread " + DonutMailAPI.unread(player.getUniqueId()));
                }
                case "events" -> report("events " + new TreeMap<>(events.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()))));
                case "place" -> {
                    // lab place <player> <dx> <dy> <dz> <material>: places a block next to a player and makes it a mailbox block.
                    Player player = online(args[1]);
                    Location base = player.getLocation();
                    Location at = base.clone().add(Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]));
                    Material material = Material.matchMaterial(args[5]);
                    Bukkit.getRegionScheduler().execute(this, at, () -> {
                        at.getBlock().setType(material);
                        String key = at.getWorld().getName() + "," + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ();
                        List<String> locations = new ArrayList<>(donutMail().settings().mailboxLocations);
                        locations.add(key);
                        donutMail().saveMailboxBlocks(locations);
                        report("place " + key);
                    });
                }
                case "click" -> {
                    // lab click <player> <x> <y> <z>: fires a real right-click event on a block (for bots whose click
                    // packets do not pass ViaBackwards).
                    Player player = online(args[1]);
                    Location at = new Location(player.getWorld(), Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]));
                    player.getScheduler().run(this, task -> {
                        org.bukkit.event.player.PlayerInteractEvent event = new org.bukkit.event.player.PlayerInteractEvent(player,
                                org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK, player.getInventory().getItemInMainHand(), at.getBlock(),
                                org.bukkit.block.BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND);
                        Bukkit.getPluginManager().callEvent(event);
                        report("click " + args[1] + " cancelled=" + event.isCancelled());
                    }, null);
                }
                case "save" -> {
                    Player player = online(args[1]);
                    player.getScheduler().run(this, task -> {
                        player.saveData();
                        report("save " + args[1]);
                    }, null);
                }
                case "sudo" -> {
                    Player player = online(args[1]);
                    String line = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                    player.getScheduler().run(this, task -> report("sudo " + args[1] + " " + line + " -> " + player.performCommand(line)), null);
                }
                default -> {
                    return false;
                }
            }
        } catch (RuntimeException exception) {
            report("error " + String.join(" ", args) + " -> " + exception);
        }
        return true;
    }
}
