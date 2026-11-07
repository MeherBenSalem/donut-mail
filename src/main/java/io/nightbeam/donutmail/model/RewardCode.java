package io.nightbeam.donutmail.model;

import java.util.List;
import java.util.Locale;

/**
 * A code players redeem with {@code /mail redeem <code>}.
 *
 * @param maxUses   total redemptions allowed (0 = unlimited)
 * @param uses      redemptions so far
 * @param perPlayer redemptions allowed per player (0 = unlimited)
 * @param expiresAt 0 = never
 */
public record RewardCode(
        String code,
        String message,
        List<String> items,
        double money,
        String voucherId,
        List<String> commands,
        int maxUses,
        int uses,
        int perPlayer,
        long expiresAt,
        long createdAt,
        String createdBy,
        boolean enabled
) {
    public RewardCode {
        code = normalize(code);
        items = items == null ? List.of() : List.copyOf(items);
        commands = commands == null ? List.of() : List.copyOf(commands);
        message = message == null ? "" : message;
        voucherId = voucherId == null || voucherId.isBlank() ? null : voucherId;
    }

    public static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    public boolean isExpired(long now) {
        return expiresAt > 0 && now >= expiresAt;
    }

    public boolean usedUp() {
        return maxUses > 0 && uses >= maxUses;
    }

    public RewardCode withCommands(List<String> newCommands) {
        return new RewardCode(code, message, items, money, voucherId, newCommands, maxUses, uses, perPlayer, expiresAt,
                createdAt, createdBy, enabled);
    }

    public RewardCode withUses(int newUses) {
        return new RewardCode(code, message, items, money, voucherId, commands, maxUses, newUses, perPlayer, expiresAt,
                createdAt, createdBy, enabled);
    }
}
