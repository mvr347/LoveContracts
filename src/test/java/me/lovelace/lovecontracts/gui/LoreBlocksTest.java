package me.lovelace.lovecontracts.gui;

import me.lovelace.lovecontracts.gui.LoreBlocks.Click;
import me.lovelace.lovecontracts.gui.LoreBlocks.Hint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoreBlocksTest {

    private static final Map<String, String> DEFAULTS = LoreBlocks.defaults();
    private static final LoreBlocks.Style STYLE = (path, fallback) -> DEFAULTS.getOrDefault(path, fallback);
    private static final String SEPARATOR = DEFAULTS.get("gui.style.separator");

    @Test
    void clicksStartWithTheSeparatorAndKeepOneActionPerLine() {
        List<String> lines = LoreBlocks.clicks(STYLE,
                Hint.of(Click.LEFT, "принять контракт"), Hint.of(Click.RIGHT, "вернуться на доску"));

        assertEquals(List.of(
                SEPARATOR,
                "<green>ЛКМ <dark_gray>— <gray>принять контракт",
                "<gold>ПКМ <dark_gray>— <gray>вернуться на доску"), lines);
    }

    @Test
    void noHintsGiveNoSeparator() {
        assertTrue(LoreBlocks.clicks(STYLE).isEmpty());
    }

    @Test
    void blockedBlockKeepsItsOrder() {
        List<String> lines = LoreBlocks.blocked(STYLE, LoreBlocks.status(STYLE, "unavailable"),
                "Не выполнены требования контракта", Hint.of(Click.LEFT, "принять контракт"));

        assertEquals(List.of(
                SEPARATOR,
                "<red>✕ Действие недоступно",
                "<red>Причина: <gray>Не выполнены требования контракта",
                "<dark_gray>ЛКМ — принять контракт (заблокировано)"), lines);
    }

    @Test
    void blockedBlockWithoutReasonOrHintHasOnlyWhatIsGiven() {
        assertEquals(List.of(SEPARATOR, "<red>✕ Заблокировано"),
                LoreBlocks.blocked(STYLE, LoreBlocks.status(STYLE, "locked"), null, null));
    }

    @Test
    void disabledNameIsNameAndUnavailableState() {
        assertEquals("<gray>Охота <dark_gray>| <red>Недоступно", LoreBlocks.disabledName(STYLE, "Охота"));
    }

    @Test
    void missingStyleKeyFallsBackToTheBuiltInValue() {
        LoreBlocks.Style empty = (path, fallback) -> fallback;

        assertEquals(SEPARATOR, LoreBlocks.separator(empty));
    }
}
