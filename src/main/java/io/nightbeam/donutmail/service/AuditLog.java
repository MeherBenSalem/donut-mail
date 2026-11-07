package io.nightbeam.donutmail.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * One line per send, claim, payment, return, expiry, removal, redemption and delivery, in
 * {@code plugins/DonutMail/logs/audit-YYYY-MM-DD.log} (UTC). Written on its own thread, in order.
 */
public final class AuditLog {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ISO_INSTANT;

    private final File folder;
    private final Logger logger;
    private final String server;
    private volatile boolean enabled;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "DonutMail-Audit");
        thread.setDaemon(true);
        return thread;
    });

    public AuditLog(File pluginFolder, Logger logger, String server, boolean enabled) {
        this.folder = new File(pluginFolder, "logs");
        this.logger = logger;
        this.server = server;
        this.enabled = enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** {@code action} then key=value pairs. Values with spaces are quoted. */
    public void log(String action, Object... keyValues) {
        if (!enabled) return;
        Instant now = Instant.now();
        StringBuilder line = new StringBuilder(STAMP.format(now)).append(' ').append(server).append(' ').append(action);
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            String value = String.valueOf(keyValues[i + 1]).replace("\n", "\\n");
            if (value.contains(" ") || value.isEmpty()) value = '"' + value.replace("\"", "'") + '"';
            line.append(' ').append(keyValues[i]).append('=').append(value);
        }
        line.append(System.lineSeparator());
        String text = line.toString();
        try {
            writer.execute(() -> {
                try {
                    if (!folder.exists() && !folder.mkdirs()) return;
                    File file = new File(folder, "audit-" + DAY.format(now) + ".log");
                    Files.writeString(file.toPath(), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException exception) {
                    logger.warning("Could not write the audit log: " + exception.getMessage());
                }
            });
        } catch (RuntimeException rejected) {
            // Shutting down.
        }
    }

    public void close() {
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
