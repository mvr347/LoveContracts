package me.lovelace.lovecontracts.npc.dialog;

import me.lovelace.lovecontracts.npc.dialog.NpcDialogWindow.Kind;
import me.lovelace.lovecontracts.npc.dialog.NpcDialogWindow.QuestLine;
import me.lovelace.lovecontracts.util.ClickGate;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NpcDialogsTest {
    private static final Set<String> GAMES = Set.of("BLACKJACK", "DICE", "RPS", "GWENT", "CARDS", "CHESS", "");

    private NpcDialogs bundled() {
        var in = getClass().getClassLoader().getResourceAsStream("npc_dialogs.yml");
        assertNotNull(in);
        return NpcDialogs.load(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
    }

    @Test
    void everyNpcHasKeyButtonsAndEnoughVariedLines() {
        var dialogs = bundled();
        assertTrue(dialogs.entries().size() >= 10, "contractor plus the nine other NPCs");
        Set<String> keys = new HashSet<>();
        for (var e : dialogs.entries()) {
            assertTrue(keys.add(e.key()), "unique key " + e.key());
            assertTrue(GAMES.contains(e.game()), e.id() + " game " + e.game());
            assertFalse(e.mainLabel().isBlank(), e.id());
            assertTrue(e.greetings().size() >= 3, e.id() + " greetings");
            assertTrue(e.smallTalk().size() >= 6, e.id() + " small talk");
            assertEquals(e.smallTalk().size(), new HashSet<>(e.smallTalk()).size(), e.id() + " no duplicate lines");
            assertEquals(NpcDialogs.Main.BOARD == e.main(), e.id().equals("contractor"), "only the contractor opens the board");
        }
    }

    @Test
    void aPlayerNeverGetsTheSameLineTwiceInARow() {
        var dialogs = bundled();
        var entry = dialogs.entries().iterator().next();
        UUID player = UUID.randomUUID();
        String previous = null;
        for (int i = 0; i < 200; i++) {
            String line = dialogs.pick(player, entry, "talk", entry.smallTalk());
            assertNotEquals(previous, line);
            previous = line;
        }
    }

    @Test
    void theQuestButtonIsFirstAndTheOptionsKeepTheirOrder() {
        var dialogs = bundled();
        var hunter = dialogs.entries().stream().filter(e -> e.id().equals("hunter")).findFirst().orElseThrow();
        var withQuest = NpcDialogWindow.options(hunter, dialogs.labels(), List.of(new QuestLine("q1", "Принести шкуры")), true);
        assertEquals(Kind.QUEST, withQuest.get(0).kind());
        assertEquals("[ЗАДАНИЕ] Принести шкуры", withQuest.get(0).label());
        assertEquals(List.of(Kind.QUEST, Kind.MAIN, Kind.GAME, Kind.SMALL_TALK, Kind.CLOSE),
                withQuest.stream().map(NpcDialogWindow.Option::kind).toList());
        assertEquals("Просмотреть доску", withQuest.get(1).label());

        var plain = NpcDialogWindow.options(hunter, dialogs.labels(), List.of(), false);
        assertEquals(List.of(Kind.MAIN, Kind.SMALL_TALK, Kind.CLOSE), plain.stream().map(NpcDialogWindow.Option::kind).toList(),
                "no quest button without a quest, no game button without LoveActivities");
    }

    @Test
    void spammedClicksAndRepeatedLinesAreDropped() {
        var gate = new ClickGate();
        UUID p = UUID.randomUUID();
        assertTrue(gate.allowClick(p, "npc", 1000, 600));
        assertFalse(gate.allowClick(p, "npc", 1200, 600));
        assertFalse(gate.allowClick(p, "npc", 1500, 600), "holding the click keeps it muted");
        assertTrue(gate.allowClick(p, "other", 1500, 600), "another NPC is independent");
        assertTrue(gate.allowClick(p, "npc", 2500, 600));

        assertTrue(gate.allowLine(p, "loading", 0, 4000));
        assertFalse(gate.allowLine(p, "loading", 1000, 4000));
        assertTrue(gate.allowLine(p, "other line", 1500, 4000));
        assertTrue(gate.allowLine(p, "loading", 2000, 4000), "a different line in between lets it through");
        assertFalse(gate.allowLine(p, "loading", 2500, 4000));
        assertTrue(gate.allowLine(p, "loading", 7000, 4000), "the window passes");
    }
}
