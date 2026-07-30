package me.lovelace.lovecontracts.gui;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.model.Contract;
import me.lovelace.lovecontracts.model.Difficulty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 54-slot Contract Board — gui-gen v1.4 compliant.
 * RULE 8: no glass filler in working zone content cells.
 */
public class ContractGUI implements Listener, InventoryHolder {

    private final LoveContracts plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final NamespacedKey contractKey;
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();
    private final Set<UUID> openInventories = ConcurrentHashMap.newKeySet();

    private static final int[] CONTRACT_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int SLOT_HEAD = 0;
    private static final int SLOT_TAB_ALL = 2;
    private static final int SLOT_TAB_ACCEPTED = 3;
    private static final int SLOT_TAB_COMPLETED = 4;
    private static final int SLOT_TAB_FAILED = 5;
    private static final int SLOT_TAB_STATS = 6;
    private static final int SLOT_REFRESH = 51;
    private static final int SLOT_CLOSE = 53;

    public ContractGUI(LoveContracts plugin) {
        this.plugin = plugin;
        this.contractKey = new NamespacedKey(plugin, "contract_id");
    }

    public void open(Player player) {
        List<Contract> contracts = plugin.getContractManager().getActiveContracts();
        Inventory inv = Bukkit.createInventory(this, 54, mm.deserialize(
                plugin.getConfig().getString("gui.title",
                        "<gradient:#55FF55:#55FFFF>Contract Board</gradient>")));

        ItemStack glass = pane();

        inv.setItem(SLOT_HEAD, headItem(player));
        inv.setItem(1, glass);
        inv.setItem(SLOT_TAB_ALL, tab(Material.COMPASS, "<gold>All Contracts</gold>", true));
        inv.setItem(SLOT_TAB_ACCEPTED, tab(Material.CHEST, "<gold>Accepted</gold>", false));
        inv.setItem(SLOT_TAB_COMPLETED, tab(Material.EMERALD, "<green>Completed</green>", false));
        inv.setItem(SLOT_TAB_FAILED, tab(Material.REDSTONE, "<red>Failed</red>", false));
        inv.setItem(SLOT_TAB_STATS, tab(Material.BOOK, "<aqua>Statistics</aqua>", false));
        inv.setItem(8, glass);

        for (int s = 9; s <= 17; s++) {
            inv.setItem(s, glass);
        }

        inv.setItem(18, glass);
        inv.setItem(26, glass);
        inv.setItem(27, glass);
        inv.setItem(35, glass);
        inv.setItem(36, glass);
        inv.setItem(44, glass);

        int idx = 0;
        for (Contract c : contracts) {
            if (idx >= CONTRACT_SLOTS.length) break;
            inv.setItem(CONTRACT_SLOTS[idx++], contractItem(c, player));
        }

        for (int s = 45; s <= 50; s++) {
            inv.setItem(s, glass);
        }
        inv.setItem(SLOT_REFRESH, button(Material.SUNFLOWER, "<yellow>Refresh</yellow>",
                List.of("<gray>Click to reload board</gray>")));
        inv.setItem(52, glass);
        inv.setItem(SLOT_CLOSE, button(Material.BARRIER, "<red>Close</red>",
                List.of("<gray>Close menu</gray>")));

        player.openInventory(inv);
        openInventories.add(player.getUniqueId());
    }

    public void refresh(Player player) {
        if (!isOpen(player)) return;
        Bukkit.getScheduler().runTask(plugin, () -> open(player));
    }

    public boolean isOpen(Player player) {
        return openInventories.contains(player.getUniqueId());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ContractGUI)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        long now = System.currentTimeMillis();
        long last = lastClick.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 300) return;
        lastClick.put(player.getUniqueId(), now);

        int slot = event.getRawSlot();
        if (slot < 0 || slot > 53) return;

        ItemStack clicked = event.getCurrentItem();

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_REFRESH) {
            refresh(player);
            return;
        }
        if (slot == SLOT_TAB_STATS) {
            plugin.getStatsGUI().open(player);
            return;
        }
        if (slot == SLOT_TAB_ALL || slot == SLOT_TAB_ACCEPTED
                || slot == SLOT_TAB_COMPLETED || slot == SLOT_TAB_FAILED) {
            refresh(player);
            return;
        }

        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        String id = meta.getPersistentDataContainer().get(contractKey, PersistentDataType.STRING);
        if (id == null || id.isEmpty()) return;

        Contract contract = plugin.getRegistry().getContract(id);
        if (contract == null) {
            player.sendMessage(mm.deserialize("<red>Contract not found.</red>"));
            refresh(player);
            return;
        }

        plugin.getContractManager().acceptContract(player, id);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ContractGUI) {
            openInventories.remove(event.getPlayer().getUniqueId());
        }
    }

    private ItemStack contractItem(Contract c, Player player) {
        Material mat = switch (c.getDifficulty()) {
            case EASY -> Material.IRON_SWORD;
            case MEDIUM -> Material.DIAMOND_SWORD;
            case HARD -> Material.NETHERITE_SWORD;
        };

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize(c.getDisplayName()));

        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<gray>" + c.getDescription() + "</gray>"));
        lore.add(Component.empty());
        lore.add(mm.deserialize("<yellow>Difficulty:</yellow> " + colorDiff(c.getDifficulty())));
        lore.add(mm.deserialize("<yellow>Type:</yellow> " +
                (c.isRepeating() ? "<gold>Repeating</gold>" : "<red>One-time</red>")));

        if (c.getCondition() != null) {
            lore.add(mm.deserialize("<yellow>Progress:</yellow> <white>" +
                    c.getCondition().getProgressString(player) + "</white>"));
        }

        lore.add(Component.empty());
        lore.add(mm.deserialize("<green>Rewards:</green>"));
        c.getRewards().forEach(r ->
                lore.add(mm.deserialize("<gold>  + " + r.getDisplay() + "</gold>")));

        boolean hasPenalty = c.getPenalties().stream()
                .anyMatch(p -> p.getType() != me.lovelace.lovecontracts.model.Penalty.Type.NONE);
        if (hasPenalty) {
            lore.add(mm.deserialize("<red>Penalties:</red>"));
            c.getPenalties().stream()
                    .filter(p -> p.getType() != me.lovelace.lovecontracts.model.Penalty.Type.NONE)
                    .forEach(p -> lore.add(mm.deserialize("<red>  - " + p.getDisplay() + "</red>")));
        }

        lore.add(Component.empty());
        boolean accepted = plugin.getContractManager().hasAcceptedToday(player.getUniqueId(), c.getId());
        lore.add(accepted
                ? mm.deserialize("<gold>✓ ACCEPTED</gold>")
                : mm.deserialize("<yellow>Click to accept</yellow>"));

        meta.lore(lore);
        meta.getPersistentDataContainer().set(contractKey, PersistentDataType.STRING, c.getId());
        item.setItemMeta(meta);
        return item;
    }

    private String colorDiff(Difficulty d) {
        return switch (d) {
            case EASY -> "<green>EASY</green>";
            case MEDIUM -> "<yellow>MEDIUM</yellow>";
            case HARD -> "<red>HARD</red>";
        };
    }

    private ItemStack pane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack tab(Material mat, String name, boolean active) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize(name + (active ? " <gray>(current)</gray>" : "")));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(mm.deserialize(active ? "<green>Selected</green>" : "<gray>Click to switch</gray>"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material mat, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize(name));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        for (String line : loreLines) {
            lore.add(mm.deserialize(line));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack headItem(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(mm.deserialize("<aqua>" + player.getName() + "</aqua>"));
        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<gray>Your contract board</gray>"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
