package me.lovelace.lovecontracts.npc.model;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public class NpcQuestStep {

    public enum Type {
        CRAFT_ITEM,
        CATCH_FISH,
        MINE_MATERIAL,
        KILL_ENTITY,
        VISIT_NPC,
        VISIT_LOCATION,
        DELIVER_ITEM
    }

    private final int stepNumber;
    private final Type type;
    private final Material material;
    private final EntityType entityType;
    private final String targetNpcKey;
    private final String targetNpcName;
    private final int count;
    private final String description;
    private final NpcDialogueNode visitDialogue;
    private final QuestLocation location;
    private final java.util.Map<String, NpcDialogueNode> reactions;
    public QuestLocation getLocation() { return location; }
    public NpcDialogueNode dialogue(String mood) {
        var variant = reactions.get(mood);
        if (variant == null) return visitDialogue;
        if (visitDialogue == null) return variant;
        return new NpcDialogueNode(variant.getNodeId(),
                variant.getTextLines().isEmpty() ? visitDialogue.getTextLines() : variant.getTextLines(),
                variant.getOptions().isEmpty() ? visitDialogue.getOptions() : variant.getOptions());
    }

    public NpcQuestStep(int stepNumber, Type type, Material material, EntityType entityType,
                        String targetNpcKey, String targetNpcName, int count, String description,
                        NpcDialogueNode visitDialogue) {
        this(stepNumber, type, material, entityType, targetNpcKey, targetNpcName, count, description, visitDialogue, null, java.util.Map.of());
    }

    public NpcQuestStep(int stepNumber, Type type, Material material, EntityType entityType,
                        String targetNpcKey, String targetNpcName, int count, String description,
                        NpcDialogueNode visitDialogue, QuestLocation location, java.util.Map<String, NpcDialogueNode> reactions) {
        this.location = location;
        this.reactions = java.util.Map.copyOf(reactions);
        this.stepNumber = stepNumber;
        this.type = type != null ? type : Type.CRAFT_ITEM;
        this.material = material;
        this.entityType = entityType;
        this.targetNpcKey = targetNpcKey;
        this.targetNpcName = targetNpcName != null ? targetNpcName : "";
        this.count = Math.max(1, count);
        this.description = description != null ? description : "";
        this.visitDialogue = visitDialogue;
    }

    public int getStepNumber() { return stepNumber; }
    public Type getType() { return type; }
    public Material getMaterial() { return material; }
    public EntityType getEntityType() { return entityType; }
    /** Key of the NPC to visit in the configured provider, or null. */
    public String getTargetNpcKey() { return targetNpcKey; }
    public String getTargetNpcName() { return targetNpcName; }
    public int getCount() { return count; }
    public String getDescription() { return description; }
    public NpcDialogueNode getVisitDialogue() { return visitDialogue; }
}
