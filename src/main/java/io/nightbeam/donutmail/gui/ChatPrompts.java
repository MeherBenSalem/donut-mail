package io.nightbeam.donutmail.gui;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** One line of chat input (a name, a note, a price, a code). The answer runs on the player's thread. */
public final class ChatPrompts implements Listener {
    private static final long TIMEOUT_TICKS = 20L * 60;

    private final DonutMailPlugin plugin;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    private record Prompt(long id, Consumer<String> answer, Consumer<End> cancelled) {
    }

    /** Why a prompt ended without an answer. */
    public enum End { CANCELLED, TIMEOUT, LEFT, SHUTDOWN }

    public ChatPrompts(DonutMailPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Asks the player for one line. {@code answer} gets the text; {@code cancelled} runs when they type the
     * cancel word, do not answer within a minute, or leave.
     */
    public void ask(Player player, String messageKey, Consumer<String> answer, Consumer<End> cancelled, String... keyValues) {
        long id = System.nanoTime();
        Prompt previous = prompts.put(player.getUniqueId(), new Prompt(id, answer, cancelled));
        if (previous != null && previous.cancelled() != null) previous.cancelled().accept(End.CANCELLED);
        player.closeInventory();
        plugin.messages().send(player, messageKey, keyValues);
        plugin.scheduler().atPlayerLater(player, () -> {
            Prompt current = prompts.get(player.getUniqueId());
            if (current != null && current.id() == id) {
                prompts.remove(player.getUniqueId());
                plugin.messages().send(player, "general.input-timeout");
                if (current.cancelled() != null) current.cancelled().accept(End.TIMEOUT);
            }
        }, TIMEOUT_TICKS);
    }

    public boolean waiting(Player player) {
        return prompts.containsKey(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Prompt prompt = prompts.remove(player.getUniqueId());
        if (prompt == null) return;
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        plugin.scheduler().atPlayer(player, () -> {
            if (text.equalsIgnoreCase(plugin.messages().raw("general.cancel-word"))) {
                if (prompt.cancelled() != null) prompt.cancelled().accept(End.CANCELLED);
            } else {
                prompt.answer().accept(text);
            }
        }, () -> {
            if (prompt.cancelled() != null) prompt.cancelled().accept(End.LEFT);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Prompt prompt = prompts.remove(event.getPlayer().getUniqueId());
        if (prompt != null && prompt.cancelled() != null) prompt.cancelled().accept(End.LEFT);
    }

    /** Ends every open prompt (plugin shutdown), so items held by a compose menu go back to their owners. */
    public void cancelAll() {
        prompts.forEach((uuid, prompt) -> {
            if (prompt.cancelled() != null) {
                try {
                    prompt.cancelled().accept(End.SHUTDOWN);
                } catch (RuntimeException ignored) {
                    // Best effort while shutting down.
                }
            }
        });
        prompts.clear();
    }
}
