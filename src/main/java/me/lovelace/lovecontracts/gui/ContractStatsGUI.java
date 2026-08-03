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
import org.bukkit.inventory.meta.SkullMeta;

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

    private static final ItemStack GLASS_PANE;
    private static final ItemStack CLOSE_BUTTON;
    private static final ItemStack BACK_BUTTON;

    static {
        GLASS_PANE = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = GLASS_PANE.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        GLASS_PANE.setItemMeta(glassMeta);

        CLOSE_BUTTON = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = CLOSE_BUTTON.getItemMeta();
        closeMeta.displayName(MiniMessage.miniMessage().deserialize("<red>Закрыть</red>"));
        closeMeta.lore(List.of(Component.empty(), MiniMessage.miniMessage().deserialize("<gray>Закрыть меню</gray>")));
        CLOSE_BUTTON.setItemMeta(closeMeta);

        BACK_BUTTON = new ItemStack(Material.ARROW);
        ItemMeta backMeta = BACK_BUTTON.getItemMeta();
        backMeta.displayName(MiniMessage.miniMessage().deserialize("<yellow>← Назад</yellow>"));
        backMeta.lore(List.of(Component.empty(), MiniMessage.miniMessage().deserialize("<gray>Вернуться к контрактам</gray>")));
        BACK_BUTTON.setItemMeta(backMeta);
    }

    public ContractStatsGUI(LoveContracts plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(this, 54,
                mm.deserialize("<aqua>Статистика контрактов</aqua>"));

        inv.setItem(0, headItem(player));
        inv.setItem(1, GLASS_PANE);
        inv.setItem(8, GLASS_PANE);

        for (int s = 9; s <= 17; s++) {
            inv.setItem(s, GLASS_PANE);
        }

        for (int s = 45; s <= 51; s++) {
            inv.setItem(s, GLASS_PANE);
        }
        inv.setItem(52, BACK_BUTTON);
        inv.setItem(53, CLOSE_BUTTON);

        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int[] stats = loadStats(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof ContractStatsGUI)) {
                    return;
                }
                inv.setItem(19, statItem(Material.EMERALD, "<green>Выполнено за день</green>", stats[0]));
                inv.setItem(20, statItem(Material.REDSTONE, "<red>Провалено за день</red>", stats[1]));
                inv.setItem(21, statItem(Material.CHEST, "<gold>Принято за день</gold>", stats[2]));
                inv.setItem(22, rateItem(stats[5] == 0 ? 0.0 : (stats[3] * 100.0) / stats[5]));
                inv.setItem(23, statItem(Material.DIAMOND, "<aqua>Всего выполнено</aqua>", stats[3]));
                inv.setItem(24, statItem(Material.COAL, "<gray>Всего провалено</gray>", stats[4]));
                inv.setItem(25, statItem(Material.BOOK, "<yellow>Всего принято</yellow>", stats[5]));
                inv.setItem(30, statItem(Material.BLAZE_POWDER, "<gold>Текущая серия</gold>", stats[6]));
                inv.setItem(32, statItem(Material.NETHER_STAR, "<light_purple>Лучшая серия</light_purple>", stats[7]));
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

        int slot = event.getRawSlot();
        if (slot == 53) {
            player.closeInventory();
        } else if (slot == 52) {
            plugin.getContractGUI().open(player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ContractStatsGUI) {
            open.remove(event.getPlayer().getUniqueId());
        }
    }

    private ItemStack headItem(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(mm.deserialize("<aqua>" + player.getName() + "</aqua>"));
        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<gray>Ваша статистика контрактов</gray>"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack statItem(Material mat, String name, int value) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize(name));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(mm.deserialize("<white>" + value + "</white>"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack rateItem(double rate) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize("<gold>Успешность</gold>"));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(mm.deserialize("<white>" + String.format("%.1f%%", rate) + "</white>"));
        meta.lore(lore);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
