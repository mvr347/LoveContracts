package me.lovelace.lovecontracts.gui;

import me.lovelace.lovecontracts.LoveContracts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ContractStatsGUI implements Listener, InventoryHolder {

    private final LoveContracts plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Set<UUID> open = ConcurrentHashMap.newKeySet();

    public ContractStatsGUI(LoveContracts plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(this, 27,
                mm.deserialize("<aqua>Contract Statistics</aqua>"));

        ItemStack glass = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        inv.setItem(22, button(Material.ARROW, "<yellow>← Back</yellow>"));

        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int[] stats = loadStats(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof ContractStatsGUI)) return;
                inv.setItem(10, statItem(Material.EMERALD, "<green>Daily Completed</green>", stats[0]));
                inv.setItem(11, statItem(Material.REDSTONE, "<red>Daily Failed</red>", stats[1]));
                inv.setItem(12, statItem(Material.CHEST, "<gold>Daily Accepted</gold>", stats[2]));
                inv.setItem(14, statItem(Material.DIAMOND, "<aqua>Total Completed</aqua>", stats[3]));
                inv.setItem(15, statItem(Material.COAL, "<gray>Total Failed</gray>", stats[4]));
                inv.setItem(16, statItem(Material.BOOK, "<yellow>Total Accepted</yellow>", stats[5]));
                double rate = stats[5] == 0 ? 0.0 : (stats[3] * 100.0) / stats[5];
                inv.setItem(13, rateItem(rate));
                inv.setItem(4, statItem(Material.BLAZE_POWDER, "<gold>Current Streak</gold>", stats[6]));
                inv.setItem(8, statItem(Material.NETHER_STAR, "<light_purple>Best Streak</light_purple>", stats[7]));
                inv.setItem(22, button(Material.ARROW, "<yellow>← Back</yellow>"));
            });
        });

        player.openInventory(inv);
        open.add(player.getUniqueId());
    }

    private int[] loadStats(UUID uuid) {
        int[] s = new int[8];
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT daily_completed, daily_failed, daily_accepted, " +
                     "total_completed, total_failed, total_accepted, " +
                     "current_streak, best_streak FROM contract_stats WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                for (int i = 0; i < 8; i++) s[i] = rs.getInt(i + 1);
            }
        } catch (Exception ignored) {
        }
        return s;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ContractStatsGUI)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() == 22) {
            plugin.getContractGUI().open(player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ContractStatsGUI) {
            open.remove(event.getPlayer().getUniqueId());
        }
    }

    private ItemStack statItem(Material mat, String name, int value) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize(name));
        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<white>" + value + "</white>"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack rateItem(double rate) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize("<gold>Success Rate</gold>"));
        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<white>" + String.format("%.1f%%", rate) + "</white>"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack pane(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize(name));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize(name));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
