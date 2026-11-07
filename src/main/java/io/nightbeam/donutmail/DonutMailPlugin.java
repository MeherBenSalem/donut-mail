package io.nightbeam.donutmail;

import io.nightbeam.donutmail.api.CampaignRequest;
import io.nightbeam.donutmail.api.Delivery;
import io.nightbeam.donutmail.api.DonutMailAPI;
import io.nightbeam.donutmail.api.DonutMailAPIBridge;
import io.nightbeam.donutmail.api.ParcelResult;
import io.nightbeam.donutmail.api.event.MailSendEvent;
import io.nightbeam.donutmail.command.MailCommand;
import io.nightbeam.donutmail.config.DailyConfig;
import io.nightbeam.donutmail.config.GuiConfig;
import io.nightbeam.donutmail.config.Messages;
import io.nightbeam.donutmail.config.Settings;
import io.nightbeam.donutmail.config.YamlFiles;
import io.nightbeam.donutmail.gui.GuiManager;
import io.nightbeam.donutmail.hook.MailPlaceholders;
import io.nightbeam.donutmail.listener.MailboxBlockListener;
import io.nightbeam.donutmail.listener.PlayerListener;
import io.nightbeam.donutmail.listener.VoteListener;
import io.nightbeam.donutmail.model.Campaign;
import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.scheduler.SchedulerAdapter;
import io.nightbeam.donutmail.service.AuditLog;
import io.nightbeam.donutmail.service.CampaignService;
import io.nightbeam.donutmail.service.CodeService;
import io.nightbeam.donutmail.service.DailyService;
import io.nightbeam.donutmail.service.DiscordWebhook;
import io.nightbeam.donutmail.service.EconomyHook;
import io.nightbeam.donutmail.service.MailService;
import io.nightbeam.donutmail.service.NetworkSync;
import io.nightbeam.donutmail.service.Notifier;
import io.nightbeam.donutmail.service.ParcelService;
import io.nightbeam.donutmail.service.SendResult;
import io.nightbeam.donutmail.service.SummaryCache;
import io.nightbeam.donutmail.service.VoucherService;
import io.nightbeam.donutmail.storage.LegacyMigrator;
import io.nightbeam.donutmail.storage.MailStorage;
import io.nightbeam.donutmail.storage.StorageFactory;
import io.nightbeam.donutmail.storage.StoragePaths;
import io.nightbeam.donutmail.storage.StorageType;
import io.nightbeam.donutmail.util.ItemSerializer;
import io.nightbeam.donutmail.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class DonutMailPlugin extends JavaPlugin implements DonutMailAPIBridge {
    private volatile Settings settings;
    private volatile Messages messages;
    private volatile GuiConfig gui;
    private volatile DailyConfig daily;
    private String serverId;

    private SchedulerAdapter scheduler;
    private MailStorage storage;
    private EconomyHook economy;
    private VoucherService vouchers;
    private AuditLog audit;
    private MailService mail;
    private ParcelService parcels;
    private CampaignService campaigns;
    private DailyService dailyRewards;
    private CodeService codes;
    private Notifier notifier;
    private SummaryCache summaries;
    private NetworkSync network;
    private DiscordWebhook discord;
    private GuiManager guiManager;
    private final Set<String> knownNames = ConcurrentHashMap.newKeySet();
    private final List<SchedulerAdapter.Cancellable> timers = new ArrayList<>();
    private boolean placeholdersHooked;
    private boolean votesHooked;

    @Override
    public void onEnable() {
        if (!isPaperFamily()) {
            getLogger().severe("DonutMail needs Paper or a Paper fork (Purpur, Folia...). This server (" + Bukkit.getName()
                    + ") is missing Paper's API, so the plugin is disabled. Paper is a drop-in replacement for Spigot: https://papermc.io/downloads");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        LegacyMigrator.migrateIfNeeded(this);
        saveResourceIfMissing("vouchers/example-voucher.yml");
        loadFiles(true);
        this.serverId = resolveServerId();

        this.scheduler = new SchedulerAdapter(this, settings.storageType() == StorageType.MYSQL ? 4 : 2);
        this.economy = new EconomyHook();
        this.vouchers = new VoucherService(this);
        this.vouchers.reload();
        this.audit = new AuditLog(getDataFolder(), getLogger(), serverId, settings.audit);

        this.storage = StorageFactory.create(this, settings, serverId);
        try {
            storage.init();
        } catch (Exception failure) {
            getLogger().log(Level.SEVERE, "DonutMail could not open its " + settings.storageType() + " storage, so it is disabled. "
                    + "Check storage settings in config.yml. Your mail is untouched.", failure);
            storage = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.summaries = new SummaryCache(this);
        this.notifier = new Notifier(this);
        this.mail = new MailService(this);
        this.parcels = new ParcelService(this);
        this.campaigns = new CampaignService(this);
        this.dailyRewards = new DailyService(this);
        this.codes = new CodeService(this);
        this.network = new NetworkSync(this);
        this.discord = new DiscordWebhook(this);
        this.guiManager = new GuiManager(this);

        campaigns.reload();
        network.start();
        try {
            knownNames.addAll(storage.knownNames(5000));
        } catch (RuntimeException failure) {
            getLogger().warning("Could not load player names for tab completion: " + failure.getMessage());
        }
        scheduler.run(() -> mail.recoverServerLocks(0));

        MailCommand command = new MailCommand(this);
        registerCommand("mail", command);
        registerCommand("mailbox", command);

        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(guiManager.prompts(), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new MailboxBlockListener(this), this);
        votesHooked = VoteListener.register(this);
        registerPlaceholders();
        startTimers();
        DonutMailAPI.registerBridge(this);

        for (Player online : Bukkit.getOnlinePlayers()) {
            PlayerListener.joined(this, online);
        }
        getLogger().info("DonutMail enabled using " + storage.type() + " storage on " + (scheduler.isFolia() ? "Folia" : "Paper")
                + " (server id " + serverId + ", economy: " + economy.name() + ").");
    }

    @Override
    public void onDisable() {
        DonutMailAPI.clearBridge();
        timers.forEach(SchedulerAdapter.Cancellable::cancel);
        timers.clear();
        if (guiManager != null) {
            try {
                guiManager.closeAll();
            } catch (RuntimeException failure) {
                getLogger().warning("Could not close open menus: " + failure.getMessage());
            }
        }
        if (scheduler != null) scheduler.shutdown();
        if (storage != null) storage.close();
        if (audit != null) audit.close();
    }

    /** Paper's API (Adventure text, region schedulers) is required; plain Spigot/Bukkit lacks it. */
    private static boolean isPaperFamily() {
        try {
            Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            return false;
        }
    }

    // ── Files ───────────────────────────────────────────────────────────────

    private void loadFiles(boolean startup) {
        File configFile = new File(getDataFolder(), "config.yml");
        boolean upgrading = configFile.exists();
        YamlConfiguration config = YamlFiles.load(this, "config.yml");
        if (startup && upgrading) migrateLegacyConfig(config, configFile);
        this.settings = new Settings(config);
        this.messages = new Messages(YamlFiles.load(this, "messages.yml"));
        this.gui = new GuiConfig(YamlFiles.load(this, "gui.yml"));
        File dailyFile = new File(getDataFolder(), "daily.yml");
        if (!dailyFile.exists()) saveResource("daily.yml", false);
        this.daily = new DailyConfig(YamlConfiguration.loadConfiguration(dailyFile), getLogger());
    }

    /** DonutMail 1.x kept a few texts in config.yml; 2.0 keeps all text in messages.yml. */
    private void migrateLegacyConfig(YamlConfiguration config, File configFile) {
        boolean changed = false;
        if (config.isConfigurationSection("messages") || config.isSet("notifications.title-main")) {
            File messagesFile = new File(getDataFolder(), "messages.yml");
            if (!messagesFile.exists()) saveResource("messages.yml", false);
            YamlConfiguration messagesYaml = YamlConfiguration.loadConfiguration(messagesFile);
            copy(config, "messages.no-permission", messagesYaml, "general.no-permission");
            copy(config, "messages.player-only", messagesYaml, "general.player-only");
            copy(config, "notifications.title-main", messagesYaml, "notify.title");
            try {
                messagesYaml.save(messagesFile);
            } catch (IOException exception) {
                getLogger().warning("Could not move 1.x messages to messages.yml: " + exception.getMessage());
            }
            for (String key : List.of("messages", "notifications.title-main", "notifications.title-sub", "notifications.chat-format")) {
                config.set(key, null);
            }
            changed = true;
            getLogger().info("Moved DonutMail 1.x message texts from config.yml to messages.yml.");
        }
        if (config.isSet("mail.page-size")) {
            config.set("mail.page-size", null);
            changed = true;
        }
        String sound = config.getString("notifications.sound-key", "");
        String converted = Sounds.fromLegacyName(sound);
        if (!Objects.equals(sound, converted)) {
            config.set("notifications.sound-key", converted == null ? "minecraft:entity.experience_orb.pickup" : converted);
            changed = true;
        }
        if (changed) {
            try {
                config.save(configFile);
            } catch (IOException exception) {
                getLogger().warning("Could not update config.yml: " + exception.getMessage());
            }
        }
    }

    private static void copy(YamlConfiguration from, String fromKey, YamlConfiguration to, String toKey) {
        String value = from.getString(fromKey);
        if (value != null && !value.isBlank()) to.set(toKey, value);
    }

    private void saveResourceIfMissing(String path) {
        if (!new File(getDataFolder(), path).exists()) saveResource(path, false);
    }

    private String resolveServerId() {
        String configured = settings.serverIdSetting;
        if (configured != null && !configured.isBlank()) return configured.trim();
        File file = new File(StoragePaths.dataDirectory(this), "server-id.txt");
        try {
            if (file.exists()) {
                String stored = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
                if (!stored.isEmpty()) return stored;
            }
            StoragePaths.ensureDataDirectory(this);
            String generated = "server-" + UUID.randomUUID().toString().substring(0, 8);
            Files.writeString(file.toPath(), generated, StandardCharsets.UTF_8);
            return generated;
        } catch (IOException exception) {
            return "server-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    public void reloadRuntime() {
        loadFiles(false);
        vouchers.reload();
        audit.setEnabled(settings.audit);
        campaigns.reloadAsync();
        timers.forEach(SchedulerAdapter.Cancellable::cancel);
        timers.clear();
        startTimers();
    }

    public void saveMailboxBlocks(List<String> locations) {
        File configFile = new File(getDataFolder(), "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        config.set("mailbox-blocks.locations", locations);
        try {
            config.save(configFile);
        } catch (IOException exception) {
            getLogger().warning("Could not save mailbox blocks: " + exception.getMessage());
        }
        this.settings = new Settings(YamlFiles.load(this, "config.yml"));
    }

    private void startTimers() {
        timers.add(scheduler.asyncTimer(() -> {
            try {
                mail.sweepExpired();
                mail.recoverServerLocks(120_000L);
            } catch (RuntimeException failure) {
                getLogger().log(Level.WARNING, "Expiry scan failed", failure);
            }
        }, 10, settings.expiryScanSeconds));
        timers.add(scheduler.asyncTimer(() -> {
            try {
                campaigns.reload();
            } catch (RuntimeException failure) {
                getLogger().log(Level.FINE, "Campaign refresh failed", failure);
            }
        }, 30, 30));
        if (network.enabled()) {
            timers.add(scheduler.asyncTimer(network::poll, settings.pollSeconds, settings.pollSeconds));
        }
        if (settings.historyRetentionDays > 0) {
            timers.add(scheduler.asyncTimer(() -> {
                try {
                    int purged = storage.purge(System.currentTimeMillis() - settings.historyRetentionDays * 86_400_000L);
                    if (purged > 0) audit.log("PURGE", "by", "retention", "days", settings.historyRetentionDays, "rows", purged);
                } catch (RuntimeException failure) {
                    getLogger().log(Level.FINE, "History cleanup failed", failure);
                }
            }, 60, 6 * 3600));
        }
    }

    private void registerPlaceholders() {
        if (!settings.placeholderApi || !getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) return;
        try {
            placeholdersHooked = new MailPlaceholders(this).register();
            if (placeholdersHooked) getLogger().info("PlaceholderAPI found: %donutmail_...% placeholders registered.");
        } catch (LinkageError | RuntimeException failure) {
            getLogger().warning("Could not register PlaceholderAPI placeholders: " + failure.getMessage());
        }
    }

    private void registerCommand(String label, MailCommand executor) {
        PluginCommand command = getCommand(label);
        if (command == null) {
            throw new IllegalStateException("Command missing from plugin.yml: " + label);
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    /** Fires an asynchronous event off the main and region threads. */
    public void fireAsync(Event event) {
        Runnable call = () -> {
            try {
                Bukkit.getPluginManager().callEvent(event);
            } catch (RuntimeException failure) {
                getLogger().warning("A listener of " + event.getEventName() + " failed: " + failure.getMessage());
            }
        };
        if (Bukkit.isPrimaryThread()) scheduler.run(call);
        else call.run();
    }

    // ── Accessors ───────────────────────────────────────────────────────────

    public Settings settings() { return settings; }
    public Messages messages() { return messages; }
    public GuiConfig gui() { return gui; }
    public DailyConfig daily() { return daily; }
    public String serverId() { return serverId; }
    public SchedulerAdapter scheduler() { return scheduler; }
    public MailStorage storage() { return storage; }
    public EconomyHook economy() { return economy; }
    public VoucherService vouchers() { return vouchers; }
    public AuditLog audit() { return audit; }
    public MailService mail() { return mail; }
    public ParcelService parcels() { return parcels; }
    public CampaignService campaigns() { return campaigns; }
    public DailyService dailyRewards() { return dailyRewards; }
    public CodeService codes() { return codes; }
    public Notifier notifier() { return notifier; }
    public SummaryCache summaries() { return summaries; }
    public NetworkSync network() { return network; }
    public DiscordWebhook discord() { return discord; }
    public GuiManager guiManager() { return guiManager; }
    public Set<String> knownNames() { return knownNames; }
    public boolean placeholdersHooked() { return placeholdersHooked; }
    public boolean votesHooked() { return votesHooked; }

    // ── API bridge ──────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<UUID> sendMail(UUID player, String sender, String message, ItemStack[] items, String voucherId) {
        Delivery.Builder builder = Delivery.to(player).sender(sender).message(message).voucher(voucherId);
        if (items != null) builder.items(Arrays.asList(items));
        return deliver(builder.build());
    }

    @Override
    public CompletableFuture<UUID> sendVoucher(UUID player, String sender, String voucherId) {
        return deliver(Delivery.to(player).sender(sender).voucher(voucherId)
                .message(messages.format("mail.voucher", "voucher", voucherId)).build());
    }

    @Override
    public CompletableFuture<UUID> deliver(Delivery delivery) {
        if (delivery.voucherId() != null && !vouchers.exists(delivery.voucherId())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown voucher: " + delivery.voucherId()));
        }
        MailSendEvent event = new MailSendEvent(delivery.kind(), delivery.receiver(), null, delivery.sender(), delivery.items(),
                delivery.money(), 0, delivery.message());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Cancelled by a MailSendEvent listener"));
        }
        long now = System.currentTimeMillis();
        MailRecord.Builder builder = mail.serverMail(delivery.kind(), delivery.receiver(), delivery.sender())
                .message(delivery.message()).items(ItemSerializer.serializeAll(delivery.items())).money(delivery.money())
                .voucherId(delivery.voucherId()).commands(delivery.commands());
        Duration expires = delivery.expiresIn();
        if (expires != null) builder.expiresAt(expires.isZero() || expires.isNegative() ? 0 : now + expires.toMillis());
        Player online = Bukkit.getPlayer(delivery.receiver());
        if (online != null) builder.receiverName(online.getName());
        return mail.deliver(builder.build()).thenApply(MailRecord::id);
    }

    @Override
    public CompletableFuture<ParcelResult> sendParcel(UUID from, String fromName, UUID to, List<ItemStack> items, String note, double codPrice) {
        Player online = Bukkit.getPlayer(to);
        return parcels.sendFromApi(from, fromName, to, online == null ? null : online.getName(), items, note, codPrice)
                .thenApply(result -> new ParcelResult(switch (result.status()) {
                    case SENT -> ParcelResult.Status.SENT;
                    case BLOCKED -> ParcelResult.Status.BLOCKED;
                    case REFUSING -> ParcelResult.Status.REFUSING;
                    case CANCELLED -> ParcelResult.Status.CANCELLED;
                    case NO_ECONOMY -> ParcelResult.Status.NO_ECONOMY;
                    case EMPTY -> ParcelResult.Status.INVALID;
                    default -> ParcelResult.Status.FAILED;
                }, result.status() == SendResult.Status.SENT ? result.mailId() : null));
    }

    @Override
    public CompletableFuture<UUID> createCampaign(CampaignRequest request) {
        long now = System.currentTimeMillis();
        Campaign campaign = new Campaign(UUID.randomUUID(), request.name(), request.sender(), request.message(),
                ItemSerializer.serializeAll(request.items()), request.money(), request.voucherId(), request.commands(),
                request.starts() == null ? 0 : request.starts().toEpochMilli(), request.ends() == null ? 0 : request.ends().toEpochMilli(),
                now, "API", 0, request.permission(), false);
        return campaigns.create(campaign).thenApply(ok -> {
            if (!ok) throw new IllegalArgumentException("A campaign named " + request.name() + " already exists");
            return campaign.id();
        });
    }

    @Override
    public CompletableFuture<Boolean> endCampaign(String name) {
        return scheduler.supply(() -> storage.campaign(name)
                .map(campaign -> storage.endCampaign(campaign.id(), System.currentTimeMillis())).orElse(false))
                .thenApply(ok -> {
                    campaigns.reloadAsync();
                    return ok;
                });
    }

    @Override
    public int unread(UUID player) {
        return summaries == null ? 0 : summaries.get(player).unread();
    }

    @Override
    public int waiting(UUID player) {
        return summaries == null ? 0 : summaries.get(player).waiting();
    }

    @Override
    public void openMailbox(Player player) {
        guiManager.openMailbox(player, MailKind.Tab.INBOX);
    }
}
