package me.lovelace.lovecontracts.npc.model;

import me.lovelace.lovecontracts.model.Reward;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NpcQuest {

    private final String id;
    /** Key of the quest-giver NPC in LoveEssential (id, name or NPC number as text), or null. */
    private final String npcKey;
    private final String displayName;
    private final boolean repeatable;
    private final int cooldownMinutes;
    private final String initialDialogueNode;
    private final Map<String, NpcDialogueNode> dialogues;
    private final List<NpcQuestStep> steps;
    private final List<Reward> rewards;
    private final QuestTerms terms;

    public NpcQuest(String id, String npcKey, String displayName, boolean repeatable, int cooldownMinutes,
                    String initialDialogueNode, Map<String, NpcDialogueNode> dialogues,
                    List<NpcQuestStep> steps, List<Reward> rewards) {
        this(id, npcKey, displayName, repeatable, cooldownMinutes, initialDialogueNode, dialogues, steps, rewards,
                QuestTerms.NONE);
    }

    public NpcQuest(String id, String npcKey, String displayName, boolean repeatable, int cooldownMinutes,
                    String initialDialogueNode, Map<String, NpcDialogueNode> dialogues,
                    List<NpcQuestStep> steps, List<Reward> rewards, QuestTerms terms) {
        this.id = id;
        this.npcKey = npcKey;
        this.displayName = displayName != null ? displayName : id;
        this.repeatable = repeatable;
        this.cooldownMinutes = Math.max(0, cooldownMinutes);
        this.initialDialogueNode = initialDialogueNode != null ? initialDialogueNode : "greeting";
        this.dialogues = dialogues != null ? new HashMap<>(dialogues) : new HashMap<>();
        this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
        this.rewards = rewards != null ? new ArrayList<>(rewards) : new ArrayList<>();
        this.terms = terms != null ? terms : QuestTerms.NONE;
    }

    /** Tier, time limit, advance and card summary; {@link QuestTerms#NONE} for a quest that declares none. */
    public QuestTerms terms() { return terms; }

    /** Base reward in coins (before speed and quality bonuses). */
    public long baseReward() {
        return Math.round(rewards.stream().mapToDouble(Reward::getAmount).sum());
    }

    public String getId() { return id; }
    public String getNpcKey() { return npcKey; }
    public String getDisplayName() { return displayName; }
    public boolean isRepeatable() { return repeatable; }
    public int getCooldownMinutes() { return cooldownMinutes; }
    public String getInitialDialogueNode() { return initialDialogueNode; }
    public NpcDialogueNode getDialogueNode(String nodeId) { return dialogues.get(nodeId); }
    public Map<String, NpcDialogueNode> getDialogues() { return Collections.unmodifiableMap(dialogues); }
    public List<NpcQuestStep> getSteps() { return Collections.unmodifiableList(steps); }
    public List<Reward> getRewards() { return Collections.unmodifiableList(rewards); }
    public int TotalSteps() { return steps.size(); }

    public NpcQuestStep getStep(int stepIndex) {
        if (stepIndex >= 0 && stepIndex < steps.size()) {
            return steps.get(stepIndex);
        }
        return null;
    }
}
