package io.nightbeam.donutmail.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlFilesTest {
    @Test
    void addsNewSettingsAndKeepsValues() throws Exception {
        YamlConfiguration server = new YamlConfiguration();
        server.loadFromString("storage:\n  type: YAML\nmail:\n  expire-days: 10\nparcels:\n  blacklist: [BEDROCK]\n");
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.loadFromString("storage:\n  type: SQLITE\n  mysql:\n    port: 3306\nmail:\n  expire-days: 30\n  expiry-scan-seconds: 60\n"
                + "parcels:\n  blacklist: [BEDROCK, BARRIER]\n  cooldown-seconds: 10\n");
        assertTrue(YamlFiles.addMissing(server, bundled));
        assertEquals("YAML", server.getString("storage.type"));
        assertEquals(10, server.getInt("mail.expire-days"));
        assertEquals(3306, server.getInt("storage.mysql.port"));
        assertEquals(60, server.getInt("mail.expiry-scan-seconds"));
        assertEquals(List.of("BEDROCK"), server.getStringList("parcels.blacklist"), "lists the owner edited stay");
        assertEquals(10, server.getInt("parcels.cooldown-seconds"));
        assertFalse(YamlFiles.addMissing(server, bundled), "nothing left to add");
    }
}
