package me.lovelace.lovecontracts.npc.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** A chain being edited in the builder: the header, the reward and the ordered steps. Free of server classes. */
public final class ChainDraft {

    /** Ids are file keys and database keys: lower-case latin, digits and underscore. */
    public static final Pattern ID = Pattern.compile("[a-z][a-z0-9_]{2,31}");
    public static final int MAX_STEPS = 20;

    private String id;
    private String displayName;
    private String giverKey;
    private boolean repeatable = true;
    private int cooldownMinutes = 1440;
    private long reward;
    /** A reward written by hand in a form the builder does not edit (for example {@code "3i"}); kept until the reward is changed. */
    private Object rewardRaw;
    /** Dialogues written by hand in chains.yml: kept as they are, so saving from the builder does not erase them. */
    private java.util.Map<String, Object> customDialogues;
    /** Achievement keys kept through editing: the builder does not edit them, but saving must not drop them. */
    private String requiredAchievement;
    private String completionAchievement;
    /** The conversation node a click on the NPC opens; the builder writes "greeting", a hand-edited file may use another. */
    private String initialDialogue = "greeting";
    /** A chain switched off by hand in chains.yml stays off when the builder rewrites the file. */
    private boolean enabled = true;
    private final List<StepDraft> steps = new ArrayList<>();

    public ChainDraft(String id) {
        this.id = id;
        this.displayName = "<gold>Новая цепочка</gold>";
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String giverKey() { return giverKey; }
    public boolean repeatable() { return repeatable; }
    public int cooldownMinutes() { return cooldownMinutes; }
    public long reward() { return reward; }
    public List<StepDraft> steps() { return steps; }

    public void id(String id) { this.id = id; }
    public void displayName(String displayName) { this.displayName = displayName == null ? "" : displayName.trim(); }
    public void giverKey(String giverKey) { this.giverKey = giverKey == null || giverKey.isBlank() ? null : giverKey.trim(); }
    public void repeatable(boolean repeatable) { this.repeatable = repeatable; }
    public void cooldownMinutes(int minutes) { this.cooldownMinutes = Math.max(0, minutes); }
    public void reward(long reward) { this.reward = Math.max(0, reward); this.rewardRaw = null; }
    public Object rewardRaw() { return rewardRaw; }
    public void rewardRaw(Object raw) { this.rewardRaw = raw; }
    public java.util.Map<String, Object> customDialogues() { return customDialogues; }
    public void customDialogues(java.util.Map<String, Object> dialogues) { this.customDialogues = dialogues; }

    public String requiredAchievement() { return requiredAchievement; }
    public String completionAchievement() { return completionAchievement; }
    public void requiredAchievement(String key) { this.requiredAchievement = key == null || key.isBlank() ? null : key.trim(); }
    public void completionAchievement(String key) { this.completionAchievement = key == null || key.isBlank() ? null : key.trim(); }

    public String initialDialogue() { return initialDialogue; }
    public boolean enabled() { return enabled; }
    public void initialDialogue(String node) { this.initialDialogue = node == null || node.isBlank() ? "greeting" : node.trim(); }
    public void enabled(boolean enabled) { this.enabled = enabled; }

    public boolean canAddStep() {
        return steps.size() < MAX_STEPS;
    }

    /** Moves a step one place earlier; false when it is already first. */
    public boolean moveUp(int index) {
        if (index <= 0 || index >= steps.size()) return false;
        StepDraft step = steps.remove(index);
        steps.add(index - 1, step);
        return true;
    }

    public ChainDraft copyAs(String newId) {
        ChainDraft copy = new ChainDraft(newId);
        copy.displayName = displayName;
        copy.giverKey = giverKey;
        copy.repeatable = repeatable;
        copy.cooldownMinutes = cooldownMinutes;
        copy.reward = reward;
        copy.rewardRaw = rewardRaw;
        copy.customDialogues = customDialogues;
        copy.initialDialogue = initialDialogue;
        copy.enabled = enabled;
        copy.requiredAchievement = requiredAchievement;
        copy.completionAchievement = completionAchievement;
        for (StepDraft step : steps) copy.steps.add(step.copy());
        return copy;
    }

    /** First free id of the form {@code base_v2}, {@code base_v3}, ... for "save as a new chain". */
    public static String nextVersionId(String base, java.util.Set<String> taken) {
        String root = base.replaceFirst("_v\\d+$", "");
        for (int version = 2; version < 1000; version++) {
            String candidate = root + "_v" + version;
            if (!taken.contains(candidate)) return candidate;
        }
        throw new IllegalStateException("no free version id for " + base);
    }
}
