package io.nightbeam.donutmail;

import io.nightbeam.donutmail.api.DonutMailAPI;
import io.nightbeam.donutmail.api.DonutMailAPIBridge;
import io.nightbeam.donutmail.command.MailCommand;
import io.nightbeam.donutmail.config.DonutMailConfig;
import io.nightbeam.donutmail.gui.GuiManager;
import io.nightbeam.donutmail.listener.GuiListener;
import io.nightbeam.donutmail.listener.PlayerJoinListener;
import io.nightbeam.donutmail.scheduler.SchedulerAdapter;
import io.nightbeam.donutmail.service.MailService;
import io.nightbeam.donutmail.service.NotificationService;
import io.nightbeam.donutmail.service.TargetResolver;
import io.nightbeam.donutmail.service.VoucherService;
import io.nightbeam.donutmail.storage.LegacyMigrator;
import io.nightbeam.donutmail.storage.MailStorage;
import io.nightbeam.donutmail.storage.StorageFactory;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class DonutMailPlugin extends JavaPlugin implements DonutMailAPIBridge {
    private SchedulerAdapter scheduler;
    private DonutMailConfig donutMailConfig;
    private MailStorage storage;
    private VoucherService voucherService;
    private MailService mailService;
    private GuiManager guiManager;
    private TargetResolver targetResolver;

    @Override
    public void onEnable() {
        LegacyMigrator.migrateIfNeeded(this);
        saveDefaultConfig();
        saveResource("vouchers/example-voucher.yml", false);

        this.scheduler = new SchedulerAdapter(this);
        this.donutMailConfig = DonutMailConfig.load(this);
        this.voucherService = new VoucherService(this);
        this.voucherService.reload();

        this.storage = StorageFactory.create(this, scheduler, donutMailConfig, donutMailConfig.storageType());
        this.storage.init().join();

        NotificationService notificationService = new NotificationService(donutMailConfig);
        this.mailService = new MailService(this, scheduler, storage, voucherService, donutMailConfig, notificationService);
        this.mailService.loadCache().join();

        this.guiManager = new GuiManager(scheduler, mailService, voucherService);
        this.targetResolver = new TargetResolver();

        MailCommand mailCommand = new MailCommand(this, guiManager, mailService, voucherService, targetResolver);
        registerCommand("mail", mailCommand);
        registerCommand("mailbox", mailCommand);

        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(mailService), this);

        DonutMailAPI.registerBridge(this);
        getLogger().info("DonutMail enabled with storage=" + storage.getType() + " folia=" + scheduler.isFolia());
    }

    @Override
    public void onDisable() {
        try {
            if (mailService != null) {
                mailService.shutdown().join();
            }
            if (storage != null) {
                storage.shutdown().join();
            }
        } catch (Exception ex) {
            getLogger().warning("Error during shutdown: " + ex.getMessage());
        }
        DonutMailAPI.clearBridge();
    }

    public void reloadPluginRuntime() {
        this.donutMailConfig = DonutMailConfig.load(this);
        this.voucherService.reload();
    }

    @Override
    public CompletableFuture<UUID> sendMail(UUID player, String sender, String message, ItemStack[] items, String voucherId) {
        List<ItemStack> attachments = items == null ? List.of() : Arrays.stream(items).toList();
        return mailService.sendMail(player, sender, message, attachments, voucherId, List.of());
    }

    @Override
    public CompletableFuture<UUID> sendVoucher(UUID player, String sender, String voucherId) {
        return mailService.sendMail(
                player,
                sender,
                "&6Voucher Delivery\n\n&fA voucher has arrived: &e" + voucherId,
                List.of(),
                voucherId,
                List.of()
        );
    }

    private void registerCommand(String commandLabel, MailCommand executor) {
        PluginCommand command = getCommand(commandLabel);
        if (command == null) {
            throw new IllegalStateException("Command missing from plugin.yml: " + commandLabel);
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
