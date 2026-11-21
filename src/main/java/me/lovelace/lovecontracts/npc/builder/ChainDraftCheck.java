package me.lovelace.lovecontracts.npc.builder;

import me.lovelace.lovecontracts.npc.model.NpcQuestStep;

import java.util.ArrayList;
import java.util.List;

/** What a chain still lacks before it can be saved: plain Russian sentences for the author. */
public final class ChainDraftCheck {

    private ChainDraftCheck() {
    }

    public static List<String> problems(ChainDraft chain) {
        List<String> problems = new ArrayList<>();
        if (chain.id() == null || !ChainDraft.ID.matcher(chain.id()).matches()) {
            problems.add("Идентификатор: латиница, цифры и «_», от 3 до 32 знаков, начинается с буквы");
        }
        if (chain.displayName() == null || chain.displayName().isBlank()) problems.add("Не задано название");
        if (chain.giverKey() == null) problems.add("Не выбран заказчик");
        if (chain.steps().isEmpty()) problems.add("Нет ни одного этапа");
        if (chain.steps().size() > ChainDraft.MAX_STEPS) problems.add("Слишком много этапов (максимум " + ChainDraft.MAX_STEPS + ")");
        for (int i = 0; i < chain.steps().size(); i++) {
            for (String problem : stepProblems(chain.steps().get(i))) {
                problems.add("Этап " + (i + 1) + ": " + problem);
            }
        }
        return problems;
    }

    public static List<String> stepProblems(StepDraft step) {
        List<String> problems = new ArrayList<>();
        if (StepDraft.usesMaterial(step.type()) && step.material() == null) problems.add("не выбран предмет");
        if (StepDraft.usesEntity(step.type()) && step.entityType() == null) problems.add("не выбрано существо");
        if (StepDraft.usesNpc(step.type()) && step.targetNpcKey() == null) problems.add("не выбран NPC");
        if (step.type() == NpcQuestStep.Type.VISIT_LOCATION && step.area() == null) problems.add("не задано место");
        if (StepDraft.usesCount(step.type()) && (step.count() < 1 || step.count() > 100_000)) problems.add("количество от 1 до 100000");
        return problems;
    }
}
