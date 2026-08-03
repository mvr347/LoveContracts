package me.lovelace.lovecontracts.npc.model;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public class NpcQuestStep {

    public enum Type {
        CRAFT_ITEM,
        MINE_MATERIAL,
        KILL_ENTITY,
        VISIT_NPC,
        DELIVER_ITEM
    }

    private final int stepNumber;
    private final Type type;
    private final Material material;
    private final EntityType entityType;
    private final int targetNpcId;
    private final String targetNpcName;
    private final int count;
    private final String description;
    private final NpcDialogueNode visitDialogue;

    public NpcQuestStep(int stepNumber, Type type, Material material, EntityType entityType,
                        int targetNpcId, String targetNpcName, int count, String description,
                        NpcDialogueNode visitDialogue) {
        this.stepNumber = stepNumber;
        this.type = type != null ? type : Type.CRAFT_ITEM;
        this.material = material;
        this.entityType = entityType;
        this.targetNpcId = targetNpcId;
        this.targetNpcName = targetNpcName != null ? targetNpcName : "";
        this.count = Math.max(1, count);
        this.description = description != null ? description : "";
        this.visitDialogue = visitDialogue;
    }

    public int getStepNumber() { return stepNumber; }
    public Type getType() { return type; }
    public Material getMaterial() { return material; }
    public EntityType getEntityType() { return entityType; }
    public int getTargetNpcId() { return targetNpcId; }
    public String getTargetNpcName() { return targetNpcName; }
    public int getCount() { return count; }
    public String getDescription() { return description; }
    public NpcDialogueNode getVisitDialogue() { return visitDialogue; }
}
