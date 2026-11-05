package de.lobbysystem.lobby.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;

public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    /** MiniMessage -> Component */
    public static Component parse(String input) {
        return MM.deserialize(input == null ? "" : input);
    }

    /** Für Item-Namen/Lore: kein automatisches Kursiv. */
    public static Component item(String input) {
        return parse(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String replace(String input, Map<String, String> placeholders) {
        String result = input == null ? "" : input;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }
}
