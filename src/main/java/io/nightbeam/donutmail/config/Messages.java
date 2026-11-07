package io.nightbeam.donutmail.config;

import io.nightbeam.donutmail.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** messages.yml: chat, titles and the action bar. */
public final class Messages {
    private final YamlConfiguration yaml;
    private final String prefix;

    public Messages(YamlConfiguration yaml) {
        this.yaml = yaml;
        this.prefix = yaml.getString("prefix", "&6&lMail &8» ");
    }

    public String raw(String path) {
        String value = yaml.getString(path);
        return value == null ? path : value;
    }

    public String format(String path, String... keyValues) {
        return Text.substitute(raw(path), Text.toMap(keyValues));
    }

    public Component component(String path, String... keyValues) {
        return Text.parse(format(path, keyValues));
    }

    public List<String> list(String path) {
        return yaml.getStringList(path);
    }

    /** Sends a message with the prefix. Empty messages are not sent, so any message can be turned off. */
    public void send(CommandSender sender, String path, String... keyValues) {
        String text = format(path, keyValues);
        if (sender == null || text.isBlank()) return;
        sender.sendMessage(Text.parse(prefix + text));
    }

    /** Sends a message without the prefix. */
    public void sendPlain(CommandSender sender, String path, String... keyValues) {
        String text = format(path, keyValues);
        if (sender == null || text.isBlank()) return;
        sender.sendMessage(Text.parse(text));
    }

    public void sendList(CommandSender sender, String path) {
        for (String line : list(path)) {
            sender.sendMessage(Text.parse(line));
        }
    }

    public List<Component> components(String path, Map<String, String> placeholders) {
        List<Component> out = new ArrayList<>();
        for (String line : list(path)) out.add(Text.parse(Text.substitute(line, placeholders)));
        return out;
    }

    public String prefix() {
        return prefix;
    }
}
