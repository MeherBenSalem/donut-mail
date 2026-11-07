package io.nightbeam.donutmail.model;

import java.util.List;
import java.util.UUID;

/**
 * One mail for every player, stored once. Each player claims it at most once (a claim row per player);
 * claiming creates their personal copy, which then goes through the normal claim flow.
 *
 * @param name         short id used in commands (unique)
 * @param startsAt     0 = right away
 * @param endsAt       0 = no end
 * @param endedAt      0 = still running; set when staff end it early
 * @param permission   optional permission a player needs to see it
 * @param existingOnly only players known before {@code createdAt} (used by {@code /mail give all})
 */
public record Campaign(
        UUID id,
        String name,
        String senderName,
        String message,
        List<String> items,
        double money,
        String voucherId,
        List<String> commands,
        long startsAt,
        long endsAt,
        long createdAt,
        String createdBy,
        long endedAt,
        String permission,
        boolean existingOnly
) {
    public Campaign {
        items = items == null ? List.of() : List.copyOf(items);
        commands = commands == null ? List.of() : List.copyOf(commands);
        message = message == null ? "" : message;
        senderName = senderName == null || senderName.isBlank() ? "Server" : senderName;
        voucherId = voucherId == null || voucherId.isBlank() ? null : voucherId;
        permission = permission == null || permission.isBlank() ? null : permission;
    }

    public boolean isLive(long now) {
        return endedAt == 0 && (startsAt == 0 || now >= startsAt) && (endsAt == 0 || now < endsAt);
    }

    public boolean isOver(long now) {
        return endedAt != 0 || (endsAt != 0 && now >= endsAt);
    }

    public boolean hasRewards() {
        return !items.isEmpty() || money > 0 || voucherId != null || !commands.isEmpty();
    }

    public Campaign withCommands(List<String> newCommands) {
        return new Campaign(id, name, senderName, message, items, money, voucherId, newCommands, startsAt, endsAt,
                createdAt, createdBy, endedAt, permission, existingOnly);
    }

    public Campaign withEndedAt(long value) {
        return new Campaign(id, name, senderName, message, items, money, voucherId, commands, startsAt, endsAt,
                createdAt, createdBy, value, permission, existingOnly);
    }
}
