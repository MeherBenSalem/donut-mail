package io.nightbeam.donutmail.service;

import io.nightbeam.donutmail.model.VoucherDefinition;
import io.nightbeam.donutmail.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VoucherService {
    private final JavaPlugin plugin;
    private final Map<String, VoucherDefinition> vouchers = new HashMap<>();

    public VoucherService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        vouchers.clear();

        File folder = new File(plugin.getDataFolder(), "vouchers");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Failed to create vouchers folder.");
            return;
        }

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String id = yaml.getString("id", "").trim();
            if (id.isEmpty()) {
                plugin.getLogger().warning("Voucher file missing id: " + file.getName());
                continue;
            }

            String name = yaml.getString("name", "&6Voucher");
            String iconString = yaml.getString("icon", "PAPER");
            Material icon;
            try {
                icon = Material.valueOf(iconString.toUpperCase());
            } catch (Exception ex) {
                icon = Material.PAPER;
            }

            List<String> lore = yaml.getStringList("lore");
            List<String> commands = yaml.getStringList("commands");

            VoucherDefinition voucher = new VoucherDefinition(
                    id,
                    name,
                    icon,
                    Text.color(lore),
                    commands
            );
            vouchers.put(id.toLowerCase(), voucher);
        }

        plugin.getLogger().info("Loaded " + vouchers.size() + " vouchers.");
    }

    public VoucherDefinition getVoucher(String id) {
        if (id == null) {
            return null;
        }
        return vouchers.get(id.toLowerCase());
    }

    public boolean exists(String id) {
        return getVoucher(id) != null;
    }

    public Collection<VoucherDefinition> all() {
        return Collections.unmodifiableCollection(vouchers.values());
    }
}
