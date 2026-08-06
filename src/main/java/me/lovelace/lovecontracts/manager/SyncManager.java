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
        if (contract == null) return;
        var comp = plugin.getMessageManager().getComponent("messages.broadcast-accept",
                "<yellow>{PLAYER}</yellow> принял(а) контракт <gold>{CONTRACT}</gold>",
                java.util.Map.of("PLAYER", player != null ? player.getName() : "Игрок", "CONTRACT", strip(contract.getDisplayName())));
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(comp));
    }

    public void broadcastComplete(Player player, Contract contract) {
        if (contract == null) return;
        var comp = plugin.getMessageManager().getComponent("messages.broadcast-complete",
                "<yellow>{PLAYER}</yellow> выполнил(а) контракт <gold>{CONTRACT}</gold>",
                java.util.Map.of("PLAYER", player != null ? player.getName() : "Игрок", "CONTRACT", strip(contract.getDisplayName())));
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(comp));
    }

    public void broadcastFail(Player player, Contract contract) {
        if (contract == null) return;
        var comp = plugin.getMessageManager().getComponent("messages.broadcast-fail",
                "<yellow>{PLAYER}</yellow> провалил(а) контракт <gold>{CONTRACT}</gold>",
                java.util.Map.of("PLAYER", player != null ? player.getName() : "Игрок", "CONTRACT", strip(contract.getDisplayName())));
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(comp));
    }

    private void broadcast(String miniMessage) {
        var component = mm.deserialize(miniMessage);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(component));
    }

    private String strip(String input) {
        return input.replaceAll("<[^>]+>", "");
    }
}
