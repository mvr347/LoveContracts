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
        if (!plugin.getConfig().getBoolean("npc.enabled", true) || !citizens.isAvailable()) {
            return;
        }
        int boundId = plugin.getConfig().getInt("npc.id", -1);
        if (boundId < 0) {
            return;
        }
        Integer npcId = citizens.npcId(event.getRightClicked());
        if (npcId == null || npcId != boundId) {
            return;
        }
        event.setCancelled(true);
        plugin.getContractGUI().open(event.getPlayer());
    }
}
