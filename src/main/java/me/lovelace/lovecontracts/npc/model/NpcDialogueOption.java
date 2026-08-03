package me.lovelace.lovecontracts.npc.model;

public class NpcDialogueOption {

    public enum Action {
        ACCEPT_QUEST,
        NEXT_NODE,
        COMPLETE_STEP,
        CLOSE
    }

    private final String text;
    private final Action action;
    private final String nextNode;

    public NpcDialogueOption(String text, Action action, String nextNode) {
        this.text = text != null ? text : "";
        this.action = action != null ? action : Action.CLOSE;
        this.nextNode = nextNode;
    }

    public String getText() { return text; }
    public Action getAction() { return action; }
    public String getNextNode() { return nextNode; }
}
