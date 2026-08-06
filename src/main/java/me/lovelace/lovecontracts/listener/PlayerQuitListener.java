package me.lovelace.lovecontracts.listener;

import me.lovelace.lovecontracts.LoveContracts;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final LoveContracts plugin;

    public PlayerQuitListener(LoveContracts plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (event.getPlayer() == null) return;
        if (plugin.getContractManager() != null) {
            plugin.getContractManager().loadActiveContractForPlayer(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer() == null) return;
        var uuid = event.getPlayer().getUniqueId();
        if (plugin.getContractGUI() != null) plugin.getContractGUI().cleanupPlayer(uuid);
        if (plugin.getContractCreateGUI() != null) plugin.getContractCreateGUI().cleanupPlayer(uuid);
        if (plugin.getConfirmGUI() != null) plugin.getConfirmGUI().cleanupPlayer(uuid);
        if (plugin.getContractManager() != null) plugin.getContractManager().cleanupPlayer(uuid);
        if (plugin.getPlayerContractBoardGUI() != null) plugin.getPlayerContractBoardGUI().cleanupPlayer(uuid);
        if (plugin.getPlayerContractMyGUI() != null) plugin.getPlayerContractMyGUI().cleanupPlayer(uuid);
        if (plugin.getPlayerContractManager() != null) plugin.getPlayerContractManager().cleanupPlayer(uuid);
        if (plugin.getNpcQuestManager() != null) plugin.getNpcQuestManager().cleanupPlayer(uuid);
    }
}
