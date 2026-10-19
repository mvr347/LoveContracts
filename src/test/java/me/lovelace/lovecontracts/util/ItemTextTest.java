package me.lovelace.lovecontracts.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemTextTest {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    @Test
    void plainTextIsNotItalicAfterwards() {
        Component out = ItemText.upright(MM.deserialize("<gold>Имя"));

        assertEquals(TextDecoration.State.FALSE, out.decoration(TextDecoration.ITALIC));
    }

    @Test
    void explicitItalicInsideTheTextIsKept() {
        Component out = ItemText.upright(MM.deserialize("<i>курсив</i>"));

        assertEquals(TextDecoration.State.TRUE, out.children().get(0).decoration(TextDecoration.ITALIC));
    }
}
