package io.nightbeam.donutmail.config;

import io.nightbeam.donutmail.util.ItemBuilder;
import io.nightbeam.donutmail.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** gui.yml: slots, materials and text of every menu. */
public final class GuiConfig {
    private final YamlConfiguration yaml;

    public GuiConfig(YamlConfiguration yaml) {
        this.yaml = yaml;
    }

    public Component title(String key, String... keyValues) {
        return Text.noItalic(Text.parse(yaml.getString("titles." + key, key), keyValues));
    }

    public String text(String path) {
        return yaml.getString(path, "");
    }

    public Component line(String path, String... keyValues) {
        return Text.parse(text(path), keyValues);
    }

    public Component line(String path, Map<String, String> placeholders) {
        return Text.parse(Text.substitute(text(path), placeholders));
    }

    public int slot(String path, int fallback) {
        return yaml.getInt(path + ".slot", fallback);
    }

    public int number(String path, int fallback) {
        return yaml.getInt(path, fallback);
    }

    /** A list of slots: '9-44', '10,11,12' or a YAML list. Slots outside the menu are dropped. */
    public List<Integer> slots(String path, int size) {
        List<Integer> out = new ArrayList<>();
        if (yaml.isList(path)) {
            for (Object value : yaml.getList(path, List.of())) {
                addSlots(out, String.valueOf(value), size);
            }
        } else {
            for (String part : yaml.getString(path, "").split(",")) {
                addSlots(out, part, size);
            }
        }
        return out;
    }

    private static void addSlots(List<Integer> out, String part, int size) {
        String value = part.trim();
        if (value.isEmpty()) return;
        try {
            int dash = value.indexOf('-');
            if (dash > 0) {
                int from = Integer.parseInt(value.substring(0, dash).trim());
                int to = Integer.parseInt(value.substring(dash + 1).trim());
                for (int i = Math.min(from, to); i <= Math.max(from, to); i++) {
                    if (i >= 0 && i < size && !out.contains(i)) out.add(i);
                }
            } else {
                int slot = Integer.parseInt(value);
                if (slot >= 0 && slot < size && !out.contains(slot)) out.add(slot);
            }
        } catch (NumberFormatException ignored) {
            // A typo in gui.yml should not break the menu.
        }
    }

    public Material material(String path, Material fallback) {
        String name = yaml.getString(path + ".material");
        if (name == null) return fallback;
        Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    /** A button from a section with material, name, lore and model. Lines that end up empty after a %placeholder% expanding to nothing are kept as spacing only when written as ''. */
    public ItemBuilder button(String path, Map<String, String> placeholders) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        Material material = material(path, Material.PAPER);
        ItemBuilder builder = ItemBuilder.of(material);
        if (section == null) {
            return builder.name(Component.text(path));
        }
        builder.name(Text.parse(Text.substitute(section.getString("name", ""), placeholders)));
        List<Component> lore = new ArrayList<>();
        for (String line : section.getStringList("lore")) {
            String filled = Text.substitute(line, placeholders);
            if (!line.isEmpty() && filled.isEmpty()) continue;
            lore.add(Text.parse(filled));
        }
        builder.lore(lore);
        builder.model(section.getInt("model", 0));
        builder.hideAll();
        return builder;
    }

    public ItemBuilder button(String path, String... keyValues) {
        return button(path, Text.toMap(keyValues));
    }

    public ItemStack filler() {
        Material material = material("filler", Material.GRAY_STAINED_GLASS_PANE);
        if (material == Material.AIR) return null;
        return ItemBuilder.of(material).name(Component.empty()).model(yaml.getInt("filler.model", 0)).hideAll().build();
    }

    public YamlConfiguration yaml() {
        return yaml;
    }
}
