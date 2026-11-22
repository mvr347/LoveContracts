package me.lovelace.lovecontracts.npc.gui;

import dev.lovelace.lovecore.api.npc.NpcHandle;
import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.npc.model.NpcDialogueNode;
import me.lovelace.lovecontracts.npc.model.NpcQuest;
import org.bukkit.entity.Player;

/**
 * Entry point for NPC conversations. Since the contract boards were unified this is only a factory: the conversation
 * window is {@link DialogueGui}, and an NPC with several conversations opens the board on its chains tab.
 */
public final class NpcDialogueGUI {

    private final LoveContracts plugin;

    public NpcDialogueGUI(LoveContracts plugin) {
        this.plugin = plugin;
    }

    /** Opens one line of a conversation; {@code expected} is the step it is for, or {@code -1} for an offer. */
    public void open(Player player, NpcQuest quest, NpcDialogueNode node, NpcHandle speaker, int expected) {
        if (player == null || quest == null || node == null) return;
        new DialogueGui(plugin, player, quest, node, speaker, expected).open();
    }
}
