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
    /** A polite or friendly answer: picking it on a running quest feeds the interaction reward bonus. */
    private final boolean polite;

    public NpcDialogueOption(String text, Action action, String nextNode) {
        this(text, action, nextNode, false);
    }

    public NpcDialogueOption(String text, Action action, String nextNode, boolean polite) {
        this.text = text != null ? text : "";
        this.action = action != null ? action : Action.CLOSE;
        this.nextNode = nextNode;
        this.polite = polite;
    }

    public boolean isPolite() { return polite; }

    public String getText() { return text; }
    public Action getAction() { return action; }
    public String getNextNode() { return nextNode; }
}
