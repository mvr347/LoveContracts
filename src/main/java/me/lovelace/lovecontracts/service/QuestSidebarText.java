package me.lovelace.lovecontracts.service;

import me.lovelace.lovecontracts.util.LoreBar;

import java.util.*;

public final class QuestSidebarText {
    public record Objective(
            String id,
            String name,
            String task,
            int step,
            int steps,
            int progress,
            int required,
            String timeLeft,
            String rewardText
    ) {}

    private QuestSidebarText() {}

    public static Objective select(List<Objective> objectives, String focus) {
        return objectives.stream().filter(o -> o.id().equals(focus)).findFirst()
                .orElse(objectives.isEmpty() ? null : objectives.getFirst());
    }

    /** Compact MiniMessage lines (4-6 rows) for the active errand scoreboard lease. */
    public static List<String> lines(Objective objective, int others) {
        if (objective == null) return List.of();
        List<String> lines = new ArrayList<>();
        add(lines, objective.name(), 1, "<gold>");
        add(lines, objective.task(), 2, "<gray>");
        if (objective.required() > 1) {
            lines.add("<gray>Прогресс: <white>" + objective.progress() + "<dark_gray>/<white>" + objective.required());
            lines.add(LoreBar.bar(objective.progress(), objective.required()));
        }
        if (objective.timeLeft() != null && !objective.timeLeft().isBlank()) {
            lines.add("<gray>Срок: <white>" + objective.timeLeft());
        }
        if (objective.rewardText() != null && !objective.rewardText().isBlank()) {
            lines.add("<gray>Награда: <white>" + objective.rewardText());
        }
        if (others > 0) {
            lines.add("<dark_gray>Ещё поручений: <gray>" + others);
        }
        return List.copyOf(lines.subList(0, Math.min(15, lines.size())));
    }

    private static void add(List<String> lines, String text, int limit, String prefix) {
        if (text == null || text.isBlank()) return;
        String remaining = text.replaceAll("\\s+", " ").trim();
        for (int i = 0; i < limit && !remaining.isEmpty(); i++) {
            int end = remaining.offsetByCodePoints(0, Math.min(32, remaining.codePointCount(0, remaining.length())));
            if (end < remaining.length()) {
                int space = remaining.lastIndexOf(' ', end);
                if (space > 10) end = space;
            }
            boolean truncated = i == limit - 1 && end < remaining.length();
            lines.add(prefix + remaining.substring(0, end) + (truncated ? "…" : ""));
            remaining = remaining.substring(end).stripLeading();
        }
    }
}
