package me.lovelace.lovecontracts.manager;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.model.Contract;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class SyncManager {

    private final LoveContracts plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public SyncManager(LoveContracts plugin) {
        this.plugin = plugin;
    }

    public void syncGUIAll() {
        Bukkit.getOnlinePlayers().forEach(this::syncGUIForPlayer);
    }

    public void syncGUIForPlayer(Player player) {
        if (player == null || !player.isOnline()) return;
        if (plugin.getContractGUI().isOpen(player)) {
            plugin.getContractGUI().refresh(player);
        }
    }

    public void broadcastAccept(Player player, Contract contract) {
        String msg = "<yellow>" + player.getName() + "</yellow> accepted <gold>"
                + strip(contract.getDisplayName()) + "</gold>";
        broadcast(msg);
    }

    public void broadcastComplete(Player player, Contract contract) {
        String msg = "<yellow>" + player.getName() + "</yellow> completed <gold>"
                + strip(contract.getDisplayName()) + "</gold>";
        broadcast(msg);
    }

    public void broadcastFail(Player player, Contract contract) {
        String msg = "<yellow>" + (player != null ? player.getName() : "Someone")
                + "</yellow> failed <gold>" + strip(contract.getDisplayName()) + "</gold>";
        broadcast(msg);
    }

    private void broadcast(String miniMessage) {
        var component = mm.deserialize(miniMessage);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(component));
    }

    private String strip(String input) {
        return input.replaceAll("<[^>]+>", "");
    }
}
