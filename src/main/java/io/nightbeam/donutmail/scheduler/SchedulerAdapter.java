package io.nightbeam.donutmail.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Threads, the same way on Paper and Folia. Paper 1.20.1+ has the region scheduler API too; on Paper
 * every call simply runs on the main thread.
 *
 * <ul>
 *   <li>storage work runs on DonutMail's own threads ({@link #storage()});</li>
 *   <li>inventories change only on the player's own thread ({@link #atPlayer});</li>
 *   <li>console commands and economy calls run on the global thread ({@link #global}).</li>
 * </ul>
 */
public final class SchedulerAdapter {
    private final Plugin plugin;
    private final boolean folia;
    private final ExecutorService storage;

    public SchedulerAdapter(Plugin plugin, int storageThreads) {
        this.plugin = plugin;
        this.folia = detectFolia();
        AtomicInteger counter = new AtomicInteger();
        this.storage = Executors.newFixedThreadPool(Math.max(1, storageThreads), runnable -> {
            Thread thread = new Thread(runnable, "DonutMail-Storage-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    public boolean isFolia() {
        return folia;
    }

    public Executor storage() {
        return storage;
    }

    public <T> CompletableFuture<T> supply(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, storage);
    }

    public CompletableFuture<Void> run(Runnable work) {
        return CompletableFuture.runAsync(work, storage);
    }

    public void global(Runnable runnable) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, runnable);
    }

    public <T> CompletableFuture<T> onGlobal(Supplier<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        global(() -> {
            try {
                future.complete(work.get());
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            }
        });
        return future;
    }

    /**
     * Runs on the entity's thread. When the entity is gone (the player logged out) {@code retired} runs
     * instead, so a started action can always be undone.
     */
    public void atPlayer(Entity entity, Runnable task, Runnable retired) {
        ScheduledTask scheduled = entity.getScheduler().run(plugin, ignored -> task.run(), retired);
        if (scheduled == null && retired != null) {
            retired.run();
        }
    }

    public void atPlayer(Entity entity, Runnable task) {
        atPlayer(entity, task, null);
    }

    public void atPlayerLater(Entity entity, Runnable task, long delayTicks) {
        entity.getScheduler().runDelayed(plugin, ignored -> task.run(), null, Math.max(1L, delayTicks));
    }

    public void globalLater(Runnable task, long delayTicks) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, ignored -> task.run(), Math.max(1L, delayTicks));
    }

    /** Repeats on a storage-friendly async thread (not a tick thread). */
    public Cancellable asyncTimer(Runnable task, long delaySeconds, long periodSeconds) {
        ScheduledTask scheduled = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, ignored -> {
            try {
                storage.execute(task);
            } catch (RuntimeException rejected) {
                // Shutting down.
            }
        }, Math.max(1, delaySeconds), Math.max(1, periodSeconds), TimeUnit.SECONDS);
        return scheduled::cancel;
    }

    public void shutdown() {
        storage.shutdown();
        try {
            if (!storage.awaitTermination(10, TimeUnit.SECONDS)) {
                storage.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            storage.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    @FunctionalInterface
    public interface Cancellable {
        void cancel();
    }
}
