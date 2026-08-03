package me.lovelace.lovecontracts.listener;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.integration.CitizensIntegration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class ContractNpcListener implements Listener {

    private final LoveContracts plugin;
    private final CitizensIntegration citizens;

    public ContractNpcListener(LoveContracts plugin, CitizensIntegration citizens) {
        this.plugin = plugin;
        this.citizens = citizens;
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        if (!citizens.isAvailable()) {
            return;
        }
        Integer npcId = citizens.npcId(event.getRightClicked());
        if (npcId == null) return;

        // Check if NPC has assigned chain quests
        var quests = plugin.getNpcQuestManager().getQuestsForNpc(npcId);
        if (!quests.isEmpty()) {
            event.setCancelled(true);
            var quest = quests.get(0);
            var dialogueNode = quest.getDialogueNode(quest.getInitialDialogueNode());
            if (dialogueNode != null) {
                plugin.getNpcDialogueGUI().open(event.getPlayer(), quest, dialogueNode);
                return;
            }
        }

        // Default bound NPC for server contracts board
        int boundId = plugin.getConfig().getInt("npc.id", -1);
        if (boundId >= 0 && npcId == boundId) {
            event.setCancelled(true);
            plugin.getContractGUI().open(event.getPlayer());
        }
    }
}
