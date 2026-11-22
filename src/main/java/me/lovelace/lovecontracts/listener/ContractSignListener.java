package me.lovelace.lovecontracts.listener;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.npc.model.NpcQuest;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ContractSignListener implements Listener {

    private final LoveContracts plugin;
    private final Set<Long> recentClicks = ConcurrentHashMap.newKeySet();

    public ContractSignListener(LoveContracts plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign)) return;

        Location loc = block.getLocation();
        Integer slot = plugin.getSignManager().getSlot(loc);
        if (slot == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        long key = player.getUniqueId().getMostSignificantBits() ^ loc.hashCode();
        if (!recentClicks.add(key)) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> recentClicks.remove(key), 8L);

        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_BLOCK) {
            // Left click -> Open full quests board
            plugin.getContractBoard().open(player);
            return;
        }

        if (action == Action.RIGHT_CLICK_BLOCK) {
            // Right click -> Open confirmation for the quest bound to this sign slot, or open the board
            List<NpcQuest> quests = new ArrayList<>(plugin.getNpcQuestManager().allQuests());
            if (slot >= 0 && slot < quests.size()) {
                NpcQuest quest = quests.get(slot);
                plugin.getContractBoard().confirmTakeChain(player, quest,
                        () -> plugin.getContractBoard().open(player),
                        () -> plugin.getNpcQuestManager().startFromBoard(player, quest));
            } else {
                plugin.getContractBoard().open(player);
            }
        }
    }
}
