package io.nightbeam.donutmail.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text in messages.yml and gui.yml: MiniMessage tags ({@code <gold>}, {@code <#ffaa00>},
 * {@code <gradient:...>}, {@code <click:...>}), legacy {@code &} codes and {@code &#RRGGBB} hex, mixed freely.
 *
 * <p>Templates are trusted. Placeholder values (player names, notes, item names typed by players) are
 * escaped, so they can never add colors, click events or fake messages. Fragments built by the plugin
 * itself (translatable item names) are wrapped with {@link #trusted(String)}.
 */
public final class Text {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Pattern PLACEHOLDER = Pattern.compile("%([A-Za-z0-9_.-]+)%");
    private static final Pattern LEGACY = Pattern.compile("(?i)[&§](?:#([0-9a-f]{6})|x((?:[&§][0-9a-f]){6})|([0-9a-fk-or&]))");
    private static final String TRUSTED = "" + randomToken() + "";
    private static final Map<Character, String> CODES = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"), Map.entry('3', "dark_aqua"),
            Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"), Map.entry('6', "gold"), Map.entry('7', "gray"),
            Map.entry('8', "dark_gray"), Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"), Map.entry('f', "white"),
            Map.entry('k', "obfuscated"), Map.entry('l', "bold"), Map.entry('m', "strikethrough"), Map.entry('n', "underlined"),
            Map.entry('o', "italic"));

    private Text() {
    }

    /** Marks a MiniMessage fragment built by the plugin so placeholder escaping keeps its tags. */
    public static String trusted(String miniMessage) {
        return TRUSTED + miniMessage;
    }

    /** Escapes a value for use inside a template: no tags, no legacy codes. */
    public static String escape(String value) {
        if (value == null) return "";
        if (value.startsWith(TRUSTED)) return value.substring(TRUSTED.length());
        return MINI.escapeTags(value.replace(TRUSTED, "")).replace("&", "&&").replace("§", "&&");
    }

    /** Substitutes escaped values into a trusted template in one pass, so values are never re-expanded. */
    public static String substitute(String template, Map<String, String> placeholders) {
        if (template == null) return "";
        if (placeholders == null || placeholders.isEmpty() || template.indexOf('%') < 0) return template;
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = placeholders.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? matcher.group() : escape(value)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static String substitute(String template, String... keyValues) {
        return substitute(template, toMap(keyValues));
    }

    /** Parses a trusted template. A malformed template is shown as plain text instead of failing. */
    public static Component parse(String message) {
        if (message == null || message.isEmpty()) return Component.empty();
        String prepared = legacyToMiniMessage(message.replace(TRUSTED, "").replace("\\n", "<newline>"));
        try {
            return MINI.deserialize(prepared);
        } catch (RuntimeException malformed) {
            return Component.text(message);
        }
    }

    public static Component parse(String template, String... keyValues) {
        return parse(substitute(template, toMap(keyValues)));
    }

    /** A menu title or item name: no default italics. */
    public static Component noItalic(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** A lore line: no default italics, gray unless the template sets a color. */
    public static Component loreLine(Component component) {
        return noItalic(component).colorIfAbsent(NamedTextColor.GRAY);
    }

    public static String plain(Component component) {
        return component == null ? "" : PLAIN.serialize(component);
    }

    /** Plain text of a template (tags and codes removed), for logs and webhooks. */
    public static String plain(String template) {
        return plain(parse(template));
    }

    /** Splits a stored message into lore lines (on newlines), wrapping long lines at word boundaries. */
    public static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        for (String paragraph : text.replace("\\n", "\n").split("\n", -1)) {
            if (paragraph.isBlank()) {
                out.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                int visible = visibleLength(line.toString()) + visibleLength(word);
                if (line.length() > 0 && visible + 1 > width) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
            if (line.length() > 0) out.add(line.toString());
        }
        return out;
    }

    private static int visibleLength(String text) {
        return text.replaceAll("<[^>]*>", "").replaceAll("(?i)[&§][0-9a-fk-or]", "").length();
    }

    /** Converts legacy {@code &}/{@code §} codes and hex to MiniMessage tags; {@code &&} is a literal ampersand. */
    static String legacyToMiniMessage(String input) {
        if (input.indexOf('&') < 0 && input.indexOf('§') < 0) return input;
        Matcher matcher = LEGACY.matcher(input);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String replacement;
            if (matcher.group(1) != null) {
                replacement = "<reset><#" + matcher.group(1).toLowerCase(Locale.ROOT) + ">";
            } else if (matcher.group(2) != null) {
                String digits = matcher.group(2).replaceAll("[&§]", "");
                replacement = "<reset><#" + digits.toLowerCase(Locale.ROOT) + ">";
            } else {
                char code = Character.toLowerCase(matcher.group(3).charAt(0));
                if (code == '&') {
                    replacement = "&";
                } else if (code == 'r') {
                    replacement = "<reset>";
                } else if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    replacement = "<reset><" + CODES.get(code) + ">";
                } else {
                    replacement = "<" + CODES.get(code) + ">";
                }
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static Map<String, String> toMap(String... keyValues) {
        Map<String, String> map = new HashMap<>();
        if (keyValues == null) return map;
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static String randomToken() {
        byte[] bytes = new byte[8];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
