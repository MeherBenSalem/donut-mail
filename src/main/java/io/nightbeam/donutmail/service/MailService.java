package io.nightbeam.donutmail.service;

import io.nightbeam.donutmail.config.DonutMailConfig;
import io.nightbeam.donutmail.model.MailEntry;
import io.nightbeam.donutmail.model.MailStatus;
import io.nightbeam.donutmail.model.VoucherDefinition;
import io.nightbeam.donutmail.scheduler.SchedulerAdapter;
import io.nightbeam.donutmail.storage.MailStorage;
import io.nightbeam.donutmail.util.InventoryUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class MailService {
    private final JavaPlugin plugin;
    private final SchedulerAdapter scheduler;
    private final MailStorage storage;
    private final VoucherService voucherService;
    private final DonutMailConfig config;
    private final NotificationService notificationService;

    private final Map<UUID, List<MailEntry>> pendingCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<MailEntry>> historyCache = new ConcurrentHashMap<>();

    public MailService(
            JavaPlugin plugin,
            SchedulerAdapter scheduler,
            MailStorage storage,
            VoucherService voucherService,
            DonutMailConfig config,
            NotificationService notificationService
    ) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.storage = storage;
        this.voucherService = voucherService;
        this.config = config;
        this.notificationService = notificationService;
    }

    public CompletableFuture<Void> loadCache() {
        return storage.loadAll().thenAccept(all -> {
            pendingCache.clear();
            historyCache.clear();
            long now = System.currentTimeMillis();
            for (MailEntry mail : all) {
                MailEntry normalized = mail;
                if (mail.getStatus() == MailStatus.PENDING && mail.isExpired(now)) {
                    normalized = mail.withStatus(MailStatus.EXPIRED, 0L);
                    storage.upsert(normalized);
                }
                cache(normalized);
            }
        });
    }

    public CompletableFuture<UUID> sendMail(UUID receiver, String sender, String message, List<ItemStack> items, String voucherId, List<String> commands) {
        long now = System.currentTimeMillis();
        long expiresAt = now + (config.expireDays() * 24L * 60L * 60L * 1000L);
        MailEntry mail = new MailEntry(
                UUID.randomUUID(),
                receiver,
                sender,
                message,
                items == null ? List.of() : items,
                voucherId,
                commands == null ? List.of() : commands,
                now,
                expiresAt,
                MailStatus.PENDING,
                0L
        );

        pendingCache.computeIfAbsent(receiver, ignored -> new ArrayList<>()).add(mail);
        sort(pendingCache.get(receiver));

        return storage.upsert(mail).thenApply(ignored -> {
            Player online = Bukkit.getPlayer(receiver);
            if (online != null && online.isOnline()) {
                scheduler.runAtPlayer(online, () -> notificationService.notifyNewMail(online));
            }
            return mail.getId();
        });
    }

    public List<MailEntry> getPending(UUID playerUuid) {
        List<MailEntry> list = new ArrayList<>(pendingCache.getOrDefault(playerUuid, List.of()));
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (MailEntry mail : new ArrayList<>(list)) {
            if (mail.isExpired(now)) {
                moveToHistory(mail.withStatus(MailStatus.EXPIRED, 0L));
                removeFromPending(mail);
                changed = true;
            }
        }
        if (changed) {
            list = new ArrayList<>(pendingCache.getOrDefault(playerUuid, List.of()));
        }
        sort(list);
        return list;
    }

    public List<MailEntry> getHistory(UUID playerUuid) {
        List<MailEntry> list = new ArrayList<>(historyCache.getOrDefault(playerUuid, List.of()));
        list.sort(Comparator.comparingLong(MailEntry::getCreatedAt).reversed());
        return list;
    }

    public int pendingCount(UUID playerUuid) {
        return getPending(playerUuid).size();
    }

    public void claimMail(Player player, UUID mailId, Consumer<ClaimResult> callback) {
        UUID uuid = player.getUniqueId();
        MailEntry target = pendingCache.getOrDefault(uuid, List.of()).stream()
                .filter(mail -> mail.getId().equals(mailId))
                .findFirst()
                .orElse(null);

        if (target == null) {
            callback.accept(ClaimResult.MAIL_NOT_FOUND);
            return;
        }

        if (target.isExpired(System.currentTimeMillis())) {
            MailEntry expired = target.withStatus(MailStatus.EXPIRED, 0L);
            removeFromPending(target);
            moveToHistory(expired);
            storage.upsert(expired);
            callback.accept(ClaimResult.MAIL_EXPIRED);
            return;
        }

        MailEntry finalTarget = target;
        scheduler.runAtPlayer(player, () -> {
            if (!InventoryUtil.canFitAll(player.getInventory(), finalTarget.getAttachments())) {
                callback.accept(ClaimResult.INVENTORY_FULL);
                return;
            }

            for (ItemStack item : finalTarget.getAttachments()) {
                if (item != null && !item.getType().isAir()) {
                    player.getInventory().addItem(item.clone());
                }
            }

            if (finalTarget.getVoucherId() != null && !finalTarget.getVoucherId().isBlank()) {
                VoucherDefinition voucher = voucherService.getVoucher(finalTarget.getVoucherId());
                if (voucher == null) {
                    callback.accept(ClaimResult.VOUCHER_MISSING);
                    return;
                }
                executeCommands(voucher.commands(), player);
            }

            executeCommands(finalTarget.getConsoleCommands(), player);

            MailEntry claimed = finalTarget.withStatus(MailStatus.CLAIMED, System.currentTimeMillis());
            removeFromPending(finalTarget);
            moveToHistory(claimed);
            storage.upsert(claimed);
            callback.accept(ClaimResult.SUCCESS);
        });
    }

    public CompletableFuture<Void> shutdown() {
        List<MailEntry> all = new ArrayList<>();
        for (List<MailEntry> mails : pendingCache.values()) {
            all.addAll(mails);
        }
        for (List<MailEntry> mails : historyCache.values()) {
            all.addAll(mails);
        }
        return storage.upsertAll(all);
    }

    public Collection<UUID> loadedUsers() {
        List<UUID> users = new ArrayList<>();
        users.addAll(pendingCache.keySet());
        users.addAll(historyCache.keySet());
        return users;
    }

    public void notifyIfHasPending(OfflinePlayer player) {
        if (!(player instanceof Player online) || !online.isOnline()) {
            return;
        }
        if (pendingCount(player.getUniqueId()) > 0) {
            scheduler.runAtPlayer(online, () -> notificationService.notifyNewMail(online));
        }
    }

    private void executeCommands(List<String> commands, Player player) {
        if (commands == null || commands.isEmpty()) {
            return;
        }

        // Folia requires command dispatch on the global region thread.
        scheduler.runGlobalSync(() -> {
            for (String command : commands) {
                if (command == null) {
                    continue;
                }

                String replaced = replacePlaceholders(command.trim(), player);

                if (replaced.isEmpty()) {
                    continue;
                }

                // Bukkit dispatch expects command lines without a leading slash.
                if (replaced.startsWith("/")) {
                    replaced = replaced.substring(1);
                }

                boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replaced);
                if (!ok) {
                    plugin.getLogger().warning("Failed to run server-side reward command: " + replaced);
                }
            }
        });
    }

    private String replacePlaceholders(String command, Player player) {
        String playerName = player.getName();
        String uuid = player.getUniqueId().toString();

        return command
                .replaceAll("(?i)%player%", playerName)
                .replaceAll("(?i)%player_name%", playerName)
                .replaceAll("(?i)%name%", playerName)
                .replaceAll("(?i)%uuid%", uuid);
    }

    private void moveToHistory(MailEntry mail) {
        historyCache.computeIfAbsent(mail.getReceiverUuid(), ignored -> new ArrayList<>()).add(mail);
        sort(historyCache.get(mail.getReceiverUuid()));
    }

    private void removeFromPending(MailEntry mail) {
        List<MailEntry> list = pendingCache.get(mail.getReceiverUuid());
        if (list != null) {
            list.removeIf(entry -> Objects.equals(entry.getId(), mail.getId()));
        }
    }

    private void cache(MailEntry mail) {
        if (mail.getStatus() == MailStatus.PENDING) {
            pendingCache.computeIfAbsent(mail.getReceiverUuid(), ignored -> new ArrayList<>()).add(mail);
            sort(pendingCache.get(mail.getReceiverUuid()));
            return;
        }
        historyCache.computeIfAbsent(mail.getReceiverUuid(), ignored -> new ArrayList<>()).add(mail);
        sort(historyCache.get(mail.getReceiverUuid()));
    }

    private void sort(List<MailEntry> mails) {
        mails.sort(Comparator.comparingLong(MailEntry::getCreatedAt).reversed());
    }
}
