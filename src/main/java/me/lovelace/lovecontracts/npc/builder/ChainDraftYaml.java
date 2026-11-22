package me.lovelace.lovecontracts.npc.builder;

import me.lovelace.lovecontracts.npc.model.NpcQuestStep;
import me.lovelace.lovecontracts.npc.model.QuestLocation;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The chains.yml form of a {@link ChainDraft}: the same keys as npc_quests.yml, so the ordinary chain loader reads it.
 * The conversations are generated (an offer at the giver, a short talk at each NPC step) unless the file has hand-written
 * ones, which are kept untouched. Free of server classes: it works on plain maps and the Bukkit configuration section.
 */
public final class ChainDraftYaml {

    public static final String GREETING_NODE = "greeting";

    private ChainDraftYaml() {
    }

    // ------------------------------------------------------------------ draft -> yaml map

    public static Map<String, Object> toMap(ChainDraft chain) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", chain.enabled());
        out.put("display-name", chain.displayName());
        out.put("npc-key", chain.giverKey());
        out.put("repeatable", chain.repeatable());
        out.put("cooldown-minutes", chain.cooldownMinutes());
        if (chain.requiredAchievement() != null) out.put("requires-achievement", chain.requiredAchievement());
        if (chain.completionAchievement() != null) out.put("completion-achievement", chain.completionAchievement());
        // a generated conversation only has the greeting node, so a custom entry point is kept only with custom dialogues
        out.put("initial-dialogue", chain.customDialogues() != null ? chain.initialDialogue() : GREETING_NODE);
        out.put("dialogues", chain.customDialogues() != null ? chain.customDialogues() : generatedDialogues());
        List<Map<String, Object>> steps = new ArrayList<>();
        for (int i = 0; i < chain.steps().size(); i++) steps.add(stepMap(i + 1, chain.steps().get(i)));
        out.put("steps", steps);
        if (chain.rewardRaw() != null) {
            out.put("rewards", Map.of("money", chain.rewardRaw()));
        } else if (chain.reward() > 0) {
            out.put("rewards", Map.of("money", chain.reward()));
        }
        return out;
    }

    private static Map<String, Object> generatedDialogues() {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("text", List.of(
                "<yellow>Заказчик:</yellow> Есть работа для тебя.",
                "<yellow>Заказчик:</yellow> Возьмёшься?"));
        node.put("options", List.of(
                option("<green>Беру поручение</green>", "ACCEPT_QUEST"),
                option("<gray>Позже</gray>", "CLOSE")));
        Map<String, Object> dialogues = new LinkedHashMap<>();
        dialogues.put(GREETING_NODE, node);
        return dialogues;
    }

    private static Map<String, Object> option(String text, String action) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("text", text);
        option.put("action", action);
        return option;
    }

    private static Map<String, Object> stepMap(int number, StepDraft step) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("step", number);
        out.put("type", step.type().name());
        if (step.material() != null) out.put("material", step.material());
        if (step.entityType() != null) out.put("entity-type", step.entityType());
        out.put("count", step.count());
        if (step.targetNpcKey() != null) {
            out.put("target-npc-key", step.targetNpcKey());
            out.put("target-npc-name", step.targetNpcName());
        }
        out.put("description", step.shownDescription());
        if (step.area() != null) out.put("location", locationMap(step.area()));
        if (step.customDialogue() != null) {
            out.put("dialogue", step.customDialogue());
        } else if (StepDraft.usesNpc(step.type())) {
            out.put("dialogue", generatedStepDialogue(step));
        }
        return out;
    }

    private static Map<String, Object> locationMap(QuestLocation area) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("world", area.world());
        out.put("x", area.x());
        out.put("y", area.y());
        out.put("z", area.z());
        out.put("radius", area.radius());
        return out;
    }

    private static Map<String, Object> generatedStepDialogue(StepDraft step) {
        String who = step.targetNpcName().isBlank() ? "NPC" : escape(step.targetNpcName());
        Map<String, Object> dialogue = new LinkedHashMap<>();
        dialogue.put("text", List.of("<yellow>" + who + ":</yellow> Слушаю тебя."));
        String confirm = step.type() == NpcQuestStep.Type.DELIVER_ITEM ? "<green>Передать предметы</green>" : "<green>Продолжить</green>";
        dialogue.put("options", List.of(option(confirm, "COMPLETE_STEP"), option("<gray>Позже</gray>", "CLOSE")));
        return dialogue;
    }

    private static String escape(String text) {
        return text.replace("<", "").replace(">", "");
    }

    // ------------------------------------------------------------------ yaml section -> draft

    /** Reads a chain written by the builder (or by hand in chains.yml). Throws on a malformed step type or location. */
    public static ChainDraft fromSection(String id, ConfigurationSection sec) {
        ChainDraft chain = new ChainDraft(id);
        chain.displayName(sec.getString("display-name", id));
        Object key = sec.get("npc-key");
        chain.giverKey(key == null ? null : key.toString());
        chain.repeatable(sec.getBoolean("repeatable", true));
        chain.cooldownMinutes(sec.getInt("cooldown-minutes", 0));
        chain.enabled(sec.getBoolean("enabled", true));
        chain.initialDialogue(sec.getString("initial-dialogue", GREETING_NODE));
        chain.requiredAchievement(sec.getString("requires-achievement"));
        chain.completionAchievement(sec.getString("completion-achievement"));
        ConfigurationSection rewards = sec.getConfigurationSection("rewards");
        if (rewards != null && rewards.contains("money")) {
            Object money = rewards.get("money");
            if (money instanceof Number number) chain.reward(number.longValue());
            else if (money != null) chain.rewardRaw(money);
        }
        ConfigurationSection dialogues = sec.getConfigurationSection("dialogues");
        if (dialogues != null && !isGenerated(dialogues)) chain.customDialogues(dialogues.getValues(false));
        List<?> steps = sec.getList("steps");
        if (steps != null) {
            for (Object entry : steps) {
                if (entry instanceof Map<?, ?> map) chain.steps().add(stepFromMap(map));
            }
        }
        return chain;
    }

    /** The conversation the builder writes itself: re-saving must not turn it into "hand-written" one. */
    private static boolean isGenerated(ConfigurationSection dialogues) {
        if (dialogues.getKeys(false).size() != 1 || !dialogues.contains(GREETING_NODE)) return false;
        List<String> text = dialogues.getStringList(GREETING_NODE + ".text");
        if (!text.equals(List.of("<yellow>Заказчик:</yellow> Есть работа для тебя.", "<yellow>Заказчик:</yellow> Возьмёшься?"))) {
            return false;
        }
        // the answers count too: stock text with the author's own choices is hand-written
        return optionsMatch(dialogues.getMapList(GREETING_NODE + ".options"),
                List.of(List.of("<green>Беру поручение</green>", "ACCEPT_QUEST"), List.of("<gray>Позже</gray>", "CLOSE")));
    }

    /** True when the options are exactly the expected (text, action) pairs, in order and without extra keys. */
    private static boolean optionsMatch(List<?> actual, List<List<String>> expected) {
        if (actual == null || actual.size() != expected.size()) return false;
        for (int i = 0; i < expected.size(); i++) {
            if (!(actual.get(i) instanceof Map<?, ?> option) || option.size() != 2) return false;
            if (!expected.get(i).get(0).equals(String.valueOf(option.get("text")))) return false;
            if (!expected.get(i).get(1).equals(String.valueOf(option.get("action")))) return false;
        }
        return true;
    }

    private static StepDraft stepFromMap(Map<?, ?> map) {
        StepDraft step = new StepDraft();
        Object type = map.get("type");
        step.type(NpcQuestStep.Type.valueOf(type == null ? "KILL_ENTITY" : type.toString().toUpperCase(Locale.ROOT)));
        step.material(text(map, "material"));
        step.entityType(text(map, "entity-type"));
        String count = text(map, "count");
        if (count != null) step.count(Integer.parseInt(count));
        String npcKey = text(map, "target-npc-key");
        if (npcKey == null) npcKey = text(map, "target-npc-id");
        String npcName = text(map, "target-npc-name");
        step.targetNpc(npcKey, npcName == null ? "" : npcName);
        String description = text(map, "description");
        // an auto-generated description is not the author's text: keep it generated so it follows later edits
        step.description(description == null ? "" : description);
        if (map.get("location") instanceof Map<?, ?> location) step.area(QuestLocation.parse(location));
        if (map.get("dialogue") instanceof Map<?, ?> dialogue && !isGeneratedStepDialogue(dialogue)) {
            Map<String, Object> copy = new LinkedHashMap<>();
            dialogue.forEach((k, v) -> copy.put(k.toString(), v));
            step.customDialogue(copy);
        }
        if (step.description().equals(step.autoDescription())) step.description("");
        return step;
    }

    private static boolean isGeneratedStepDialogue(Map<?, ?> dialogue) {
        Object text = dialogue.get("text");
        if (!(text instanceof List<?> list) || list.size() != 1 || list.get(0) == null
                || !list.get(0).toString().endsWith(":</yellow> Слушаю тебя.")) {
            return false;
        }
        Object options = dialogue.get("options");
        List<?> actual = options instanceof List<?> l ? l : null;
        return optionsMatch(actual, List.of(List.of("<green>Передать предметы</green>", "COMPLETE_STEP"), List.of("<gray>Позже</gray>", "CLOSE")))
                || optionsMatch(actual, List.of(List.of("<green>Продолжить</green>", "COMPLETE_STEP"), List.of("<gray>Позже</gray>", "CLOSE")));
    }

    private static String text(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }
}
