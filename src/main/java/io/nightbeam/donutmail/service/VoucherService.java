package io.nightbeam.donutmail.service;

import io.nightbeam.donutmail.model.VoucherDefinition;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Vouchers: reward bundles in plugins/DonutMail/vouchers/*.yml (name, icon, lore, console commands). */
public final class VoucherService {
    private final JavaPlugin plugin;
    private volatile Map<String, VoucherDefinition> vouchers = Map.of();

    public VoucherService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        Map<String, VoucherDefinition> loaded = new ConcurrentHashMap<>();
        File folder = new File(plugin.getDataFolder(), "vouchers");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Failed to create vouchers folder.");
            vouchers = loaded;
            return;
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String id = yaml.getString("id", "").trim();
                if (id.isEmpty()) {
                    plugin.getLogger().warning("Voucher file missing id: " + file.getName());
                    continue;
                }
                Material icon = Material.matchMaterial(yaml.getString("icon", "PAPER").toUpperCase(Locale.ROOT));
                List<String> lore = yaml.getStringList("lore");
                loaded.put(id.toLowerCase(Locale.ROOT), new VoucherDefinition(id, yaml.getString("name", "&6Voucher"),
                        icon == null ? Material.PAPER : icon, lore, yaml.getStringList("commands")));
            }
        }
        vouchers = loaded;
        plugin.getLogger().info("Loaded " + loaded.size() + " voucher(s).");
    }

    public VoucherDefinition getVoucher(String id) {
        return id == null ? null : vouchers.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean exists(String id) {
        return getVoucher(id) != null;
    }

    public Collection<VoucherDefinition> all() {
        return Collections.unmodifiableCollection(vouchers.values());
    }
}
