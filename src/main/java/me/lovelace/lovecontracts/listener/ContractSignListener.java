package me.lovelace.lovecontracts.listener;

import me.lovelace.lovecontracts.LoveContracts;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public class ContractSignListener implements Listener {

    private final LoveContracts plugin;
    private final Set<Long> recentClicks = ConcurrentHashMap.newKeySet();

    public ContractSignListener(LoveContracts plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign sign)) return;

        String header = sign.getSide(Side.FRONT).getLine(0);
        if (!header.equalsIgnoreCase("[LoveContracts]") &&
            !header.equalsIgnoreCase("§4[LoveContracts]") &&
            !header.contains("LoveContracts")) {
            return;
        }

        Player player = event.getPlayer();
        long key = player.getUniqueId().getMostSignificantBits() ^ block.getLocation().hashCode();
        if (!recentClicks.add(key)) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> recentClicks.remove(key), 10L);

        String contractId = sign.getSide(Side.FRONT).getLine(1).trim();
        if (contractId.isEmpty()) {
            player.sendMessage("§cInvalid sign configuration");
            return;
        }

        event.setCancelled(true);
        plugin.getContractManager().acceptContract(player, contractId);
    }
}
