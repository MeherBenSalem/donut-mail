package io.nightbeam.donutmail.util;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

public final class Text {
    private Text() {
    }

    public static String color(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input.replace("\\n", "\n"));
    }

    public static List<String> color(List<String> input) {
        List<String> out = new ArrayList<>();
        for (String line : input) {
            out.add(color(line));
        }
        return out;
    }

    public static String preview(String input, int maxLength) {
        if (input == null) {
            return "";
        }
        String stripped = ChatColor.stripColor(color(input));
        if (stripped == null) {
            return "";
        }
        return stripped.length() <= maxLength ? stripped : stripped.substring(0, maxLength - 3) + "...";
    }

    public static List<String> toLoreLines(String input, int maxLineLength) {
        List<String> out = new ArrayList<>();
        if (input == null || input.isEmpty()) {
            return out;
        }

        String rendered = color(input);
        String[] splitByNewline = rendered.split("\\n", -1);
        int limit = Math.max(12, maxLineLength);

        for (String line : splitByNewline) {
            if (line.isEmpty()) {
                out.add(" ");
                continue;
            }

            if (line.length() <= limit) {
                out.add(line);
                continue;
            }

            for (int i = 0; i < line.length(); i += limit) {
                int end = Math.min(line.length(), i + limit);
                out.add(line.substring(i, end));
            }
        }

        return out;
    }
}
