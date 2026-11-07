package io.nightbeam.donutmail.service;

import io.nightbeam.donutmail.model.Campaign;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordWebhookTest {
    @Test
    void buildsAnEmbedWithoutMentions() {
        Campaign campaign = new Campaign(UUID.randomUUID(), "summer", "Staff", "<gold>Summer \"event\"</gold>\n@everyone come!",
                List.of("nbt1:A"), 500, null, List.of(), 0, 1_800_000_000_000L, 1, "test", 0, null, false);
        String json = DiscordWebhook.payload(campaign, "Mail", "https://example.com/a.png", "#00FF00", List.of("1 item stack(s)", "$500"),
                Instant.parse("2026-11-07T12:00:00Z"));
        assertTrue(json.contains("\"username\":\"Mail\""), json);
        assertTrue(json.contains("\"allowed_mentions\":{\"parse\":[]}"), json);
        assertTrue(json.contains("Summer \\\"event\\\"\\n@everyone come!"), json);
        assertTrue(json.contains("\"color\":65280"), json);
        assertTrue(json.contains("<t:1800000000:R>"), json);
        assertTrue(json.contains("1 item stack(s)\\n$500"), json);
        assertEquals(0xF5A623, DiscordWebhook.color("nope"));
    }
}
