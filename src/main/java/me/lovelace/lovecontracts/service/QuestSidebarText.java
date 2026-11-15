package me.lovelace.lovecontracts.service;

import java.util.*;

public final class QuestSidebarText {
    public record Objective(String id, String name, String task, int step, int steps,
                            int progress, int required, String npc, String location, String status) {}
    private QuestSidebarText() {}

    public static Objective select(List<Objective> objectives, String focus) {
        return objectives.stream().filter(o -> o.id().equals(focus)).findFirst()
                .orElse(objectives.isEmpty() ? null : objectives.getFirst());
    }

    public static List<String> lines(Objective objective, int others) {
        if (objective == null) return List.of();
        List<String> lines = new ArrayList<>();
        add(lines, objective.name(), 2);
        lines.add("Этап " + objective.step() + "/" + objective.steps());
        lines.add("Цель:");
        add(lines, objective.task(), 3);
        lines.add("Прогресс: " + objective.progress() + "/" + objective.required());
        if (objective.npc() != null && !objective.npc().isBlank()) add(lines, "К кому: " + objective.npc(), 2);
        if (objective.location() != null && !objective.location().isBlank()) add(lines, "Где: " + objective.location(), 2);
        if (objective.status() != null && !objective.status().isBlank()) add(lines, objective.status(), 2);
        if (others > 0) lines.add("Других поручений: " + others);
        return List.copyOf(lines.subList(0, Math.min(15, lines.size())));
    }

    private static void add(List<String> lines, String text, int limit) {
        if (text == null || text.isBlank()) return;
        String remaining = text.replaceAll("\\s+", " ").trim();
        for (int i = 0; i < limit && !remaining.isEmpty(); i++) {
            int end = remaining.offsetByCodePoints(0, Math.min(32, remaining.codePointCount(0, remaining.length())));
            if (end < remaining.length()) {
                int space = remaining.lastIndexOf(' ', end);
                if (space > 10) end = space;
            }
            boolean truncated = i == limit - 1 && end < remaining.length();
            lines.add(remaining.substring(0, end) + (truncated ? "…" : ""));
            remaining = remaining.substring(end).stripLeading();
        }
    }
}
