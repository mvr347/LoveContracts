package me.lovelace.lovecontracts.npc.builder;

import me.lovelace.lovecontracts.npc.model.NpcQuestStep;
import me.lovelace.lovecontracts.npc.model.QuestLocation;

import java.util.Locale;

/**
 * One step of a chain being edited. Plain strings for the material and the entity: the builder GUI checks them against
 * the server's registries, and this model stays free of server classes so it can be tested.
 */
public final class StepDraft {

    private NpcQuestStep.Type type = NpcQuestStep.Type.KILL_ENTITY;
    private String material;
    private String entityType;
    private int count = 1;
    private String targetNpcKey;
    private String targetNpcName = "";
    private String description = "";
    private QuestLocation area;
    /** A conversation written by hand for this step: kept as it is. */
    private java.util.Map<String, Object> customDialogue;

    public StepDraft() {
    }

    public StepDraft(NpcQuestStep.Type type) {
        this.type = type;
        normalize();
    }

    public NpcQuestStep.Type type() { return type; }
    public String material() { return material; }
    public String entityType() { return entityType; }
    public int count() { return count; }
    public String targetNpcKey() { return targetNpcKey; }
    public String targetNpcName() { return targetNpcName; }
    public String description() { return description; }
    public QuestLocation area() { return area; }
    public java.util.Map<String, Object> customDialogue() { return customDialogue; }
    public void customDialogue(java.util.Map<String, Object> dialogue) { this.customDialogue = dialogue; }

    public void type(NpcQuestStep.Type type) { this.type = type; normalize(); }
    public void material(String material) { this.material = blankToNull(material); }
    public void entityType(String entityType) { this.entityType = entityType == null || entityType.isBlank() ? null : entityType.trim().toUpperCase(Locale.ROOT); }
    public void count(int count) { this.count = Math.max(1, count); normalize(); }
    public void targetNpc(String key, String name) { this.targetNpcKey = blankToNull(key); this.targetNpcName = name == null ? "" : name; }
    public void description(String description) { this.description = description == null ? "" : description.trim(); }
    public void area(QuestLocation area) { this.area = area; }

    /** Which fields a step of this type has: the builder shows exactly these buttons. */
    public static boolean usesMaterial(NpcQuestStep.Type type) {
        return type == NpcQuestStep.Type.MINE_MATERIAL || type == NpcQuestStep.Type.CRAFT_ITEM || type == NpcQuestStep.Type.DELIVER_ITEM;
    }

    public static boolean usesEntity(NpcQuestStep.Type type) {
        return type == NpcQuestStep.Type.KILL_ENTITY;
    }

    public static boolean usesNpc(NpcQuestStep.Type type) {
        return type == NpcQuestStep.Type.VISIT_NPC || type == NpcQuestStep.Type.DELIVER_ITEM;
    }

    public static boolean usesCount(NpcQuestStep.Type type) {
        return type != NpcQuestStep.Type.VISIT_NPC && type != NpcQuestStep.Type.VISIT_LOCATION;
    }

    /** Only the area of a "go there" step is required; for the other counted steps it restricts where they count. */
    public static boolean usesArea(NpcQuestStep.Type type) {
        return type != NpcQuestStep.Type.VISIT_NPC && type != NpcQuestStep.Type.DELIVER_ITEM;
    }

    /** Drops what the current type does not use, so a changed type never leaves a stale target in the file. */
    private void normalize() {
        if (!usesMaterial(type)) material = null;
        if (!usesEntity(type)) entityType = null;
        if (!usesNpc(type)) { targetNpcKey = null; targetNpcName = ""; }
        if (!usesCount(type)) count = 1;
        if (!usesArea(type)) area = null;
    }

    /** A line for the step when the author wrote none. */
    public String autoDescription() {
        return switch (type) {
            case KILL_ENTITY -> "Убейте: " + nameOr(entityType) + " × " + count;
            case MINE_MATERIAL -> "Добудьте: " + nameOr(material) + " × " + count;
            case CRAFT_ITEM -> "Скрафтите: " + nameOr(material) + " × " + count;
            case CATCH_FISH -> "Поймайте рыбу × " + count;
            case DELIVER_ITEM -> "Передайте: " + nameOr(material) + " × " + count;
            case VISIT_NPC -> "Поговорите с " + (targetNpcName.isBlank() ? "NPC" : targetNpcName);
            case VISIT_LOCATION -> "Придите на место" + (area == null ? "" : ": " + area.address());
        };
    }

    /** What the card shows: the author's text, or the generated one. */
    public String shownDescription() {
        return description.isBlank() ? autoDescription() : description;
    }

    private static String nameOr(String value) {
        return value == null ? "?" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public StepDraft copy() {
        StepDraft copy = new StepDraft();
        copy.type = type;
        copy.material = material;
        copy.entityType = entityType;
        copy.count = count;
        copy.targetNpcKey = targetNpcKey;
        copy.targetNpcName = targetNpcName;
        copy.description = description;
        copy.area = area;
        copy.customDialogue = customDialogue;
        return copy;
    }
}
