package me.lovelace.lovecontracts.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.List;

/**
 * Item names and lore of the GUIs. Minecraft draws custom names and lore in italics unless the text switches italics
 * off, so every item builder passes its text through here. The text is wrapped in a parent that sets italics off:
 * an italic tag inside the text keeps its own style (explicit styles of a child win over the parent's).
 */
public final class ItemText {

    private ItemText() {
    }

    public static Component upright(Component text) {
        return Component.empty().decoration(TextDecoration.ITALIC, false).append(text);
    }

    public static List<Component> upright(List<Component> lines) {
        return lines.stream().map(ItemText::upright).toList();
    }
}
