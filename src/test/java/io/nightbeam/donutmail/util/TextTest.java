package io.nightbeam.donutmail.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTest {
    @Test
    void playerTextCannotFormat() {
        String template = "&6From &e%player%";
        Component parsed = Text.parse(Text.substitute(template, "player", "<click:run_command:'/op me'>Steve&c</click>"));
        String plain = Text.plain(parsed);
        assertTrue(plain.contains("<click:run_command:'/op me'>Steve&c</click>"), plain);
        assertFalse(containsClick(parsed));
    }

    @Test
    void trustedFragmentsKeepTheirTags() {
        Component parsed = Text.parse(Text.substitute("Item: %item%", "item", Text.trusted("<lang:item.minecraft.diamond>")));
        assertFalse(Text.plain(parsed).contains("<lang"));
    }

    @Test
    void legacyAndMiniMessageMix() {
        Component parsed = Text.parse("&aGreen <click:run_command:'/mail'><gold>[Open]</gold></click>");
        assertEquals("Green [Open]", Text.plain(parsed));
        assertTrue(containsClick(parsed));
        assertEquals(NamedTextColor.GREEN, firstColor(parsed));
        assertEquals("<reset><#ffaa00>x", Text.legacyToMiniMessage("&#FFAA00x"));
        assertEquals("a & b", Text.plain(Text.parse("a && b")));
    }

    @Test
    void malformedTemplatesFallBackToPlainText() {
        assertEquals("<gold unclosed", Text.plain(Text.parse("<gold unclosed")));
    }

    @Test
    void wrapsAtWords() {
        List<String> lines = Text.wrap("one two three four five six seven", 10);
        assertEquals(List.of("one two", "three four", "five six", "seven"), lines);
        assertEquals(List.of("a", "", "b"), Text.wrap("a\n\nb", 10));
        assertTrue(Text.wrap("", 10).isEmpty());
    }

    private static boolean containsClick(Component component) {
        ClickEvent click = component.clickEvent();
        if (click != null) return true;
        for (Component child : component.children()) {
            if (containsClick(child)) return true;
        }
        return false;
    }

    private static Object firstColor(Component component) {
        if (component.color() != null) return component.color();
        for (Component child : component.children()) {
            Object color = firstColor(child);
            if (color != null) return color;
        }
        assertNull(null);
        return null;
    }
}
