package me.lovelace.lovecontracts.npc.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NpcDialogueNode {

    private final String nodeId;
    private final List<String> textLines;
    private final List<NpcDialogueOption> options;

    public NpcDialogueNode(String nodeId, List<String> textLines, List<NpcDialogueOption> options) {
        this.nodeId = nodeId;
        this.textLines = textLines != null ? new ArrayList<>(textLines) : new ArrayList<>();
        this.options = options != null ? new ArrayList<>(options) : new ArrayList<>();
    }

    public String getNodeId() { return nodeId; }
    public List<String> getTextLines() { return Collections.unmodifiableList(textLines); }
    public List<NpcDialogueOption> getOptions() { return Collections.unmodifiableList(options); }
}
