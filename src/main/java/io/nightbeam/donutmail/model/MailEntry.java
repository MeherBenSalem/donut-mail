package io.nightbeam.donutmail.model;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class MailEntry {
    private final UUID id;
    private final UUID receiverUuid;
    private final String sender;
    private final String message;
    private final List<ItemStack> attachments;
    private final String voucherId;
    private final List<String> consoleCommands;
    private final long createdAt;
    private final long expiresAt;
    private final MailStatus status;
    private final long claimedAt;

    public MailEntry(
            UUID id,
            UUID receiverUuid,
            String sender,
            String message,
            List<ItemStack> attachments,
            String voucherId,
            List<String> consoleCommands,
            long createdAt,
            long expiresAt,
            MailStatus status,
            long claimedAt
    ) {
        this.id = id;
        this.receiverUuid = receiverUuid;
        this.sender = sender;
        this.message = message;
        this.attachments = attachments == null ? List.of() : List.copyOf(attachments);
        this.voucherId = voucherId;
        this.consoleCommands = consoleCommands == null ? List.of() : List.copyOf(consoleCommands);
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = status;
        this.claimedAt = claimedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReceiverUuid() {
        return receiverUuid;
    }

    public String getSender() {
        return sender;
    }

    public String getMessage() {
        return message;
    }

    public List<ItemStack> getAttachments() {
        return Collections.unmodifiableList(attachments);
    }

    public String getVoucherId() {
        return voucherId;
    }

    public List<String> getConsoleCommands() {
        return Collections.unmodifiableList(consoleCommands);
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public MailStatus getStatus() {
        return status;
    }

    public long getClaimedAt() {
        return claimedAt;
    }

    public boolean isExpired(long now) {
        return expiresAt > 0 && now >= expiresAt;
    }

    public MailEntry withStatus(MailStatus newStatus, long newClaimedAt) {
        return new MailEntry(
                id,
                receiverUuid,
                sender,
                message,
                new ArrayList<>(attachments),
                voucherId,
                new ArrayList<>(consoleCommands),
                createdAt,
                expiresAt,
                newStatus,
                newClaimedAt
        );
    }
}
