package io.nightbeam.donutmail.service;

import io.nightbeam.donutmail.DonutMailPlugin;
import io.nightbeam.donutmail.config.Settings;
import io.nightbeam.donutmail.model.Campaign;
import io.nightbeam.donutmail.util.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Posts new campaigns to a Discord channel. */
public final class DiscordWebhook {
    private final DonutMailPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public DiscordWebhook(DonutMailPlugin plugin) {
        this.plugin = plugin;
    }

    public void campaign(Campaign campaign) {
        Settings settings = plugin.settings();
        if (!settings.discordEnabled || settings.discordUrl == null || !settings.discordUrl.startsWith("http")) return;
        String body = payload(campaign, settings.discordUsername, settings.discordAvatar, settings.discordColor,
                rewards(campaign), Instant.now());
        HttpRequest request = HttpRequest.newBuilder(URI.create(settings.discordUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("User-Agent", "DonutMail")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, failure) -> {
            if (failure != null) {
                plugin.getLogger().warning("Discord webhook failed: " + failure.getMessage());
            } else if (response.statusCode() == 429) {
                long wait = response.headers().firstValueAsLong("retry-after").orElse(2);
                plugin.scheduler().globalLater(() -> http.sendAsync(request, HttpResponse.BodyHandlers.discarding()),
                        Math.max(20, wait * 20));
            } else if (response.statusCode() >= 300) {
                plugin.getLogger().warning("Discord webhook answered " + response.statusCode() + ": " + response.body());
            }
        });
    }

    private List<String> rewards(Campaign campaign) {
        List<String> out = new ArrayList<>();
        if (!campaign.items().isEmpty()) out.add(campaign.items().size() + " item stack(s)");
        if (campaign.money() > 0) out.add(plugin.economy().format(campaign.money()));
        if (campaign.voucherId() != null) out.add("voucher " + campaign.voucherId());
        if (!campaign.commands().isEmpty()) out.add(campaign.commands().size() + " bonus reward(s)");
        return out;
    }

    /** The JSON body; kept separate so it can be tested without a server. */
    static String payload(Campaign campaign, String username, String avatar, String color, List<String> rewards, Instant now) {
        StringBuilder fields = new StringBuilder();
        if (!rewards.isEmpty()) {
            fields.append("{\"name\":\"Rewards\",\"value\":").append(json(String.join("\n", rewards))).append(",\"inline\":true}");
        }
        if (campaign.endsAt() > 0) {
            if (fields.length() > 0) fields.append(',');
            fields.append("{\"name\":\"Ends\",\"value\":").append(json("<t:" + campaign.endsAt() / 1000 + ":R>")).append(",\"inline\":true}");
        }
        String description = Text.plain(campaign.message());
        if (description.length() > 3900) description = description.substring(0, 3900) + "...";
        StringBuilder out = new StringBuilder("{");
        out.append("\"username\":").append(json(username == null || username.isBlank() ? "Server Mail" : username));
        if (avatar != null && avatar.startsWith("http")) out.append(",\"avatar_url\":").append(json(avatar));
        out.append(",\"allowed_mentions\":{\"parse\":[]}");
        out.append(",\"embeds\":[{\"title\":").append(json("Mail from " + campaign.senderName()))
                .append(",\"description\":").append(json(description.isBlank() ? "New server mail" : description))
                .append(",\"color\":").append(color(color))
                .append(",\"footer\":{\"text\":\"Claim it in game: /mail\"}")
                .append(",\"timestamp\":").append(json(now.toString()));
        if (fields.length() > 0) out.append(",\"fields\":[").append(fields).append(']');
        out.append("}]}");
        return out.toString();
    }

    static int color(String hex) {
        try {
            return Integer.parseInt(hex.replace("#", "").trim().toLowerCase(Locale.ROOT), 16);
        } catch (RuntimeException invalid) {
            return 0xF5A623;
        }
    }

    static String json(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
