package io.nightbeam.donutmail.listener;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.config.Settings;
import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.PlayerRecord;
import io.nightbeam.donutmail.service.Rewards;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Vote rewards through the mailbox. Listens to NuVotifier's VotifierEvent without a compile-time
 * dependency, so DonutMail runs the same with or without it. Offline voters get the reward when they
 * open their mailbox.
 */
public final class VoteListener implements Listener {
    private static final String EVENT_CLASS = "com.vexsoftware.votifier.model.VotifierEvent";

    private final DonutMailPlugin plugin;

    private VoteListener(DonutMailPlugin plugin) {
        this.plugin = plugin;
    }

    /** Registers the listener when NuVotifier is installed. Returns whether it was registered. */
    @SuppressWarnings("unchecked")
    public static boolean register(DonutMailPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("Votifier") == null && Bukkit.getPluginManager().getPlugin("NuVotifier") == null) {
            return false;
        }
        try {
            Class<? extends Event> type = (Class<? extends Event>) Class.forName(EVENT_CLASS);
            VoteListener listener = new VoteListener(plugin);
            EventExecutor executor = (ignored, event) -> {
                if (type.isInstance(event)) listener.onVote(event);
            };
            Bukkit.getPluginManager().registerEvent(type, listener, EventPriority.MONITOR, executor, plugin, true);
            return true;
        } catch (ClassNotFoundException | LinkageError | ClassCastException missing) {
            plugin.getLogger().warning("NuVotifier is installed but its vote event was not found; vote rewards are off.");
            return false;
        }
    }

    private void onVote(Event event) {
        Settings settings = plugin.settings();
        if (!settings.votesEnabled) return;
        String username;
        String service;
        try {
            Object vote = event.getClass().getMethod("getVote").invoke(event);
            Method name = vote.getClass().getMethod("getUsername");
            Method from = vote.getClass().getMethod("getServiceName");
            username = String.valueOf(name.invoke(vote));
            service = String.valueOf(from.invoke(vote));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            plugin.getLogger().warning("Could not read a vote: " + failure.getMessage());
            return;
        }
        if (username == null || username.isBlank() || "null".equals(username)) return;
        plugin.scheduler().run(() -> {
            UUID id = null;
            String name = username;
            Player online = Bukkit.getPlayerExact(username);
            if (online != null) {
                id = online.getUniqueId();
                name = online.getName();
            } else {
                Optional<PlayerRecord> known = plugin.storage().playerByName(username);
                if (known.isPresent()) {
                    id = known.get().id();
                } else {
                    OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(username);
                    if (cached != null && cached.hasPlayedBefore()) id = cached.getUniqueId();
                }
            }
            if (id == null) {
                plugin.audit().log("VOTE_UNKNOWN", "player", username, "service", service);
                return;
            }
            String message = settings.voteMessage == null || settings.voteMessage.isBlank()
                    ? plugin.messages().format("mail.vote", "service", service)
                    : io.nightbeam.donutmail.util.Text.substitute(settings.voteMessage, "service", service);
            MailRecord reward = plugin.mail().serverMail(MailKind.VOTE, id, service).receiverName(name).message(message)
                    .items(Rewards.encoded(settings.voteItems, plugin.getLogger(), "config.yml votes"))
                    .money(settings.voteMoney).commands(settings.voteCommands).voucherId(settings.voteVoucher).build();
            plugin.mail().deliver(reward);
            plugin.audit().log("VOTE", "player", name, "service", service, "mail", reward.id());
        });
    }
}
