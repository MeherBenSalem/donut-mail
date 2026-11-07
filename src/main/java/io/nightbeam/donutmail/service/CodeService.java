package io.nightbeam.donutmail.service;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.api.event.CodeRedeemEvent;
import io.nightbeam.donutmail.model.MailKind;
import io.nightbeam.donutmail.model.MailRecord;
import io.nightbeam.donutmail.model.RedeemOutcome;
import io.nightbeam.donutmail.model.RewardCode;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Reward codes: the use is counted (total and per player) and the reward saved in one transaction. */
public final class CodeService {
    public static final Pattern VALID = Pattern.compile("[A-Za-z0-9_-]{3,32}");

    private final DonutMailPlugin plugin;
    private final Map<UUID, Long> lastAttempt = new ConcurrentHashMap<>();

    public CodeService(DonutMailPlugin plugin) {
        this.plugin = plugin;
    }

    /** Null when the player must wait before trying again. */
    public CompletableFuture<RedeemOutcome> redeem(Player player, String input) {
        long now = System.currentTimeMillis();
        int cooldown = plugin.settings().codeAttemptCooldown;
        Long last = lastAttempt.get(player.getUniqueId());
        if (cooldown > 0 && last != null && now - last < cooldown * 1000L) {
            return CompletableFuture.completedFuture(null);
        }
        lastAttempt.put(player.getUniqueId(), now);
        String code = RewardCode.normalize(input);
        if (!VALID.matcher(code).matches()) {
            return CompletableFuture.completedFuture(RedeemOutcome.UNKNOWN);
        }
        return plugin.scheduler().supply(() -> {
            RewardCode stored = plugin.storage().code(code).orElse(null);
            if (stored == null) return RedeemOutcome.UNKNOWN;
            MailRecord reward = plugin.mail().serverMail(MailKind.CODE, player.getUniqueId(), stored.code())
                    .receiverName(player.getName())
                    .message(stored.message().isBlank() ? plugin.messages().format("mail.code", "code", stored.code()) : stored.message())
                    .items(stored.items()).money(stored.money()).voucherId(stored.voucherId()).commands(stored.commands())
                    .build();
            RedeemOutcome outcome = plugin.storage().redeem(code, player.getUniqueId(), reward, now);
            if (outcome == RedeemOutcome.OK) {
                plugin.audit().log("REDEEM", "code", code, "player", player.getName(), "mail", reward.id());
                plugin.fireAsync(new CodeRedeemEvent(player.getUniqueId(), code, reward.id()));
                plugin.summaries().refresh(player.getUniqueId());
            }
            return outcome;
        });
    }
}
