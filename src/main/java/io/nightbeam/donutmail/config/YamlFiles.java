package io.nightbeam.donutmail.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/** Loads a bundled YAML file from the data folder, adding settings that are new in this version. */
public final class YamlFiles {
    private YamlFiles() {
    }

    public static YamlConfiguration load(JavaPlugin plugin, String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration defaults = bundled(plugin, name);
        if (defaults != null) {
            yaml.setDefaults(defaults);
            if (addMissing(yaml, defaults)) {
                try {
                    yaml.save(file);
                } catch (IOException exception) {
                    plugin.getLogger().log(Level.WARNING, "Could not add new settings to " + name, exception);
                }
            }
        }
        return yaml;
    }

    public static YamlConfiguration bundled(JavaPlugin plugin, String name) {
        try (InputStream stream = plugin.getResource(name)) {
            if (stream == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            return null;
        }
    }

    /** Copies keys that exist in the bundled file but not in the server's, keeping every existing value. */
    static boolean addMissing(YamlConfiguration target, YamlConfiguration defaults) {
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) {
                continue;
            }
            if (!target.isSet(key) && !hasParentValue(target, key)) {
                target.set(key, defaults.get(key));
                changed = true;
            }
        }
        return changed;
    }

    /** A parent key with a plain value (for example a list replaced by a scalar) is the owner's choice. */
    private static boolean hasParentValue(YamlConfiguration target, String key) {
        int dot = key.lastIndexOf('.');
        while (dot > 0) {
            String parent = key.substring(0, dot);
            if (target.isSet(parent) && !target.isConfigurationSection(parent)) return true;
            dot = parent.lastIndexOf('.');
        }
        return false;
    }
}
