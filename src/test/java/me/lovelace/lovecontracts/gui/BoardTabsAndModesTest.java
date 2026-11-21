package me.lovelace.lovecontracts.gui;

import me.lovelace.lovecontracts.gui.BoardModes.ChainFilter;
import me.lovelace.lovecontracts.gui.BoardModes.ChainSort;
import me.lovelace.lovecontracts.gui.BoardModes.MineFilter;
import me.lovelace.lovecontracts.gui.BoardModes.MineSort;
import me.lovelace.lovecontracts.gui.BoardModes.Mode;
import me.lovelace.lovecontracts.gui.BoardModes.OfferFilter;
import me.lovelace.lovecontracts.gui.BoardModes.OfferSort;
import me.lovelace.lovecontracts.gui.board.BoardTab;
import me.lovelace.lovecontracts.npc.model.ChainStatus;
import me.lovelace.lovecontracts.player.model.PlayerContractObjectiveType;
import me.lovelace.lovecontracts.player.model.PlayerContractStatus;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The tabs and the filter/sort options of the unified board. */
class BoardTabsAndModesTest {

    private static YamlConfiguration messages() throws Exception {
        try (InputStream in = BoardTabsAndModesTest.class.getResourceAsStream("/messages.yml")) {
            assertNotNull(in);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void theBoardHasFourTabsAndTheyFillTheHeaderTogetherWithFilterAndSort() {
        assertEquals(4, BoardTab.values().length);
        assertEquals(BoardTab.SYSTEM, BoardTab.values()[0], "the daily contracts come first");
        // four tabs + filter + sort = six controls = all of slots 2-7, on every tab, so tabs never jump
        assertEquals(6, BoardTab.values().length + 2);
    }

    @Test
    void everyTabHasItsTextsInMessagesYml() throws Exception {
        YamlConfiguration cfg = messages();
        for (BoardTab tab : BoardTab.values()) {
            assertNotNull(cfg.getString("gui.tab." + tab.key()), "name of " + tab);
            assertNotNull(cfg.getString("gui.tab." + tab.key() + "-lore"), "description of " + tab);
        }
    }

    @Test
    void everyModeHasItsNameInMessagesYmlEqualToTheFallback() throws Exception {
        YamlConfiguration cfg = messages();
        List<Mode> all = new ArrayList<>();
        for (Mode[] group : new Mode[][]{ChainFilter.values(), ChainSort.values(), OfferFilter.values(),
                OfferSort.values(), MineFilter.values(), MineSort.values()}) {
            all.addAll(List.of(group));
        }
        for (Mode mode : all) {
            assertEquals(mode.fallback(), cfg.getString(mode.messageKey()), mode.messageKey());
        }
    }

    @Test
    void newModesCycleAroundBothWays() {
        for (ChainFilter m : ChainFilter.values()) assertEquals(m, m.next().prev());
        for (ChainSort m : ChainSort.values()) assertEquals(m, m.prev().next());
        for (OfferFilter m : OfferFilter.values()) assertEquals(m, m.next().prev());
        for (OfferSort m : OfferSort.values()) assertEquals(m, m.prev().next());
        for (MineFilter m : MineFilter.values()) assertEquals(m, m.next().prev());
        for (MineSort m : MineSort.values()) assertEquals(m, m.prev().next());
        assertEquals(ChainFilter.ALL, ChainFilter.DONE.next());
        assertEquals(ChainFilter.DONE, ChainFilter.ALL.prev());
    }

    @Test
    void chainFilterShowsLockedAndLoadingChainsOnlyUnderAll() {
        for (ChainStatus status : ChainStatus.values()) assertTrue(ChainFilter.ALL.accepts(status));
        assertFalse(ChainFilter.AVAILABLE.accepts(ChainStatus.LOCKED));
        assertFalse(ChainFilter.AVAILABLE.accepts(ChainStatus.LOADING));
        assertTrue(ChainFilter.AVAILABLE.accepts(ChainStatus.AVAILABLE));
        assertTrue(ChainFilter.ACTIVE.accepts(ChainStatus.ACTIVE));
        assertTrue(ChainFilter.COOLDOWN.accepts(ChainStatus.COOLDOWN));
        assertTrue(ChainFilter.DONE.accepts(ChainStatus.DONE));
        assertFalse(ChainFilter.DONE.accepts(ChainStatus.COOLDOWN));
    }

    @Test
    void offerFilterSelectsByWhatTheOfferAsksFor() {
        assertTrue(OfferFilter.ALL.accepts(PlayerContractObjectiveType.CUSTOM));
        assertTrue(OfferFilter.KILL.accepts(PlayerContractObjectiveType.KILL_ENTITY));
        assertFalse(OfferFilter.KILL.accepts(PlayerContractObjectiveType.DELIVER_ITEM));
        assertTrue(OfferFilter.DELIVER.accepts(PlayerContractObjectiveType.DELIVER_ITEM));
        assertTrue(OfferFilter.CUSTOM.accepts(PlayerContractObjectiveType.CUSTOM));
        assertFalse(OfferFilter.CUSTOM.accepts(PlayerContractObjectiveType.KILL_ENTITY));
    }

    @Test
    void mineFilterSplitsByRoleAndByWhetherTheContractIsStillRunning() {
        assertTrue(MineFilter.CREATOR.accepts(true, PlayerContractStatus.OPEN));
        assertFalse(MineFilter.CREATOR.accepts(false, PlayerContractStatus.OPEN));
        assertTrue(MineFilter.EXECUTOR.accepts(false, PlayerContractStatus.IN_PROGRESS));
        assertFalse(MineFilter.EXECUTOR.accepts(true, PlayerContractStatus.IN_PROGRESS));
        assertTrue(MineFilter.RUNNING.accepts(true, PlayerContractStatus.PENDING_REVIEW));
        assertFalse(MineFilter.RUNNING.accepts(true, PlayerContractStatus.COMPLETED));
        assertFalse(MineFilter.RUNNING.accepts(false, PlayerContractStatus.EXPIRED));
        assertTrue(MineFilter.ALL.accepts(false, PlayerContractStatus.CANCELLED));
    }

    @Test
    void theOldModesKeepTheirKeys() {
        assertEquals("filters.easy", BoardModes.FilterMode.EASY.messageKey());
        assertEquals("sorts.difficulty", BoardModes.SortMode.DIFFICULTY.messageKey());
        assertEquals("gui.modes.chain-filter.cooldown", ChainFilter.COOLDOWN.messageKey());
    }
}
