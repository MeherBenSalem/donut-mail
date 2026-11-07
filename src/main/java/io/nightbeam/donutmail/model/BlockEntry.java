package io.nightbeam.donutmail.model;

import java.util.UUID;

/** {@code owner} refuses parcels from {@code blocked}. */
public record BlockEntry(UUID owner, UUID blocked, String blockedName, long createdAt) {
}
