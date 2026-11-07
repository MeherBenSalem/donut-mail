package io.nightbeam.donutmail.model;

import java.util.UUID;

/**
 * A change another server should know about (MySQL only). {@code player} is who to refresh or notify;
 * it is null for campaign changes.
 */
public record NetworkEvent(long id, long createdAt, String origin, String type, UUID player, UUID mailId) {
    public static final String NEW_MAIL = "NEW";
    public static final String UPDATE = "UPDATE";
    public static final String CAMPAIGN = "CAMPAIGN";
}
