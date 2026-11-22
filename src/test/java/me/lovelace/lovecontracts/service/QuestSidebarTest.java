package me.lovelace.lovecontracts.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestSidebarTest {
    private static class Provider implements SidebarLeaseTracker.Provider<String> {
        int active, opened, closed, updates;
        @Override public SidebarLeaseTracker.Lease<String> show(UUID player, String board) {
            assertEquals(0, active, "renewal must work even when LE allows one lease per player");
            active++; opened++;
            return new SidebarLeaseTracker.Lease<>() {
                boolean released;
                public void update(String value) { updates++; }
                public void close() { if (!released) { active--; closed++; released = true; } }
            };
        }
    }
    @Test void changesUpdateContentAndRenewalReleasesOldLease() {
        var tracker = new SidebarLeaseTracker<String>();
        var provider = new Provider();
        var player = UUID.randomUUID();
        tracker.provider(provider);
        tracker.render(player, "Шаг 1", 0);
        tracker.render(player, "Шаг 2", 100);
        tracker.render(player, "Шаг 2", 200);
        assertEquals(1, provider.opened); assertEquals(1, provider.updates);
        tracker.render(player, "Шаг 2", 500_000_000);
        assertEquals(2, provider.opened); assertEquals(1, provider.closed); assertEquals(1, provider.active);
    }
    @Test void completingLastQuestAndDisconnectReleaseOverride() {
        var tracker = new SidebarLeaseTracker<String>();
        var provider = new Provider();
        var player = UUID.randomUUID();
        tracker.provider(provider);
        tracker.render(player, "Квест", 0);
        tracker.render(player, null, 1);
        tracker.remove(player);
        assertEquals(0, provider.active); assertEquals(1, provider.closed);
        tracker.render(player, "Новый квест", 2);
        tracker.remove(player);
        assertEquals(2, provider.closed);
    }
    @Test void providerReloadAndPluginDisableReleaseAllOwnedRequests() {
        var tracker = new SidebarLeaseTracker<String>();
        var first = new Provider(); var next = new Provider(); var player = UUID.randomUUID();
        tracker.provider(first); tracker.render(player, "Квест", 0);
        tracker.provider(next); assertEquals(0, first.active);
        tracker.render(player, "Квест", 1); tracker.close(); tracker.close();
        assertEquals(0, next.active); assertEquals(1, next.closed);
        tracker.provider(null); tracker.render(player, "Квест", 2);
        assertEquals(1, next.opened);
    }
    @Test void objectiveFocusFallsBackAfterCompletionAndEmptyRestoresDefault() {
        var a = objective("first"); var b = objective("second");
        assertSame(b, QuestSidebarText.select(List.of(a,b), "second"));
        assertSame(a, QuestSidebarText.select(List.of(a), "second"));
        assertNull(QuestSidebarText.select(List.of(), "second"));
        assertEquals(List.of(), QuestSidebarText.lines(null, 0));
    }
    @Test void compactSidebarIncludesTitleGoalBarTimeAndRewardWithinLimit() {
        var objective = objective("story");
        var lines = QuestSidebarText.lines(objective, 2);
        assertTrue(lines.size() <= 8, "compact sidebar should stay short: " + lines);
        assertTrue(lines.stream().anyMatch(s -> s.contains("Помощь деревне")));
        assertTrue(lines.stream().anyMatch(s -> s.contains("3") && s.contains("8")));
        assertTrue(lines.stream().anyMatch(s -> s.contains("█")), "16-cell progress bar");
        assertTrue(lines.stream().anyMatch(s -> s.contains("45 мин")));
        assertTrue(lines.stream().anyMatch(s -> s.contains("180 монет")));
        assertTrue(lines.stream().anyMatch(s -> s.contains("Ещё поручений: <gray>2")));
    }
    private QuestSidebarText.Objective objective(String id) {
        return new QuestSidebarText.Objective(id, "Помощь деревне", "Принесите травнице грибы",
                2, 4, 3, 8, "45 мин", "180 монет");
    }
}
