package me.lovelace.lovecontracts.player.gui;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.player.manager.PlayerContractManager;
import me.lovelace.lovecontracts.player.model.PlayerContract;
import me.lovelace.lovecontracts.player.model.PlayerContractObjectiveType;
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
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Доска открытых player-to-player контрактов. Клик по карточке = попытка принять.
 * 54-слотовый layout, стекло только по краю (строка 0-8, 45-53, колонки 0/8) — рабочая
 * зона 9-44 остаётся пустой, как в {@link me.lovelace.lovecontracts.gui.ContractGUI}.
 */
public class PlayerContractBoardGUI implements Listener, InventoryHolder {

    private final LoveContracts plugin;
    private final PlayerContractManager manager;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final NamespacedKey idKey;
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();
    private final Set<UUID> openInventories = ConcurrentHashMap.newKeySet();

    private static final int[] CONTRACT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int SLOT_REFRESH = 49;
    private static final int SLOT_CLOSE = 53;

    public PlayerContractBoardGUI(LoveContracts plugin, PlayerContractManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.idKey = new NamespacedKey(plugin, "player_contract_id");
    }

    public void open(Player player) {
        manager.listOpenFor(player).thenAccept(contracts ->
                Bukkit.getScheduler().runTask(plugin, () -> render(player, contracts)));
    }

    private void render(Player player, List<PlayerContract> contracts) {
        Inventory inv = Bukkit.createInventory(this, 54, mm.deserialize(
                plugin.getConfig().getString("player-contracts.board.title",
                        "<gradient:#55FF55:#55FFFF>Player Contracts</gradient>")));

        ItemStack glass = pane();
        for (int s = 0; s <= 8; s++) inv.setItem(s, glass);
        for (int s = 45; s <= 53; s++) inv.setItem(s, glass);
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9, glass);
            inv.setItem(row * 9 + 8, glass);
        }

        int idx = 0;
        List<PlayerContract> excludingOwn = contracts.stream()
                .filter(c -> !c.getCreatorId().equals(player.getUniqueId()))
                .toList();
        for (PlayerContract c : excludingOwn) {
            if (idx >= CONTRACT_SLOTS.length) break;
            inv.setItem(CONTRACT_SLOTS[idx++], contractItem(c));
        }

        inv.setItem(SLOT_REFRESH, button(Material.SUNFLOWER, "<yellow>Обновить</yellow>",
                List.of("<gray>Перезагрузить доску</gray>")));
        inv.setItem(SLOT_CLOSE, button(Material.BARRIER, "<red>Закрыть</red>", List.of()));

        player.openInventory(inv);
        openInventories.add(player.getUniqueId());
    }

    public boolean isOpen(Player player) {
        return openInventories.contains(player.getUniqueId());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PlayerContractBoardGUI)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        long now = System.currentTimeMillis();
        long last = lastClick.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 300) return;
        lastClick.put(player.getUniqueId(), now);

        int slot = event.getRawSlot();
        if (slot < 0 || slot > 53) return;

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_REFRESH) {
            open(player);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String idStr = clicked.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (idStr == null) return;

        UUID contractId = UUID.fromString(idStr);
        manager.accept(player, contractId).thenAccept(result ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(mm.deserialize(result.message()));
                    open(player);
                }));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof PlayerContractBoardGUI) {
            openInventories.remove(event.getPlayer().getUniqueId());
        }
    }

    private ItemStack contractItem(PlayerContract c) {
        Material mat = switch (c.getObjectiveType()) {
            case DELIVER_ITEM -> Material.CHEST;
            case KILL_ENTITY -> Material.IRON_SWORD;
            case CUSTOM -> Material.WRITTEN_BOOK;
        };

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize("<gold>" + c.getDescription() + "</gold>"));

        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<gray>Наниматель:</gray> <white>" + c.getCreatorName() + "</white>"));
        lore.add(mm.deserialize("<gray>Задача:</gray> <white>" + objectiveLabel(c) + "</white>"));
        lore.add(Component.empty());
        lore.add(mm.deserialize("<green>Награда:</green> <gold>" + c.getGoldReward() + " монет</gold>"));
        if (!c.getItemRewards().isEmpty()) {
            lore.add(mm.deserialize("<green>+ " + c.getItemRewards().size() + " предмет(ов)</green>"));
        }
        lore.add(Component.empty());
        lore.add(mm.deserialize("<yellow>Срок:</yellow> <white>" + hoursLeft(c) + "</white>"));
        lore.add(Component.empty());
        lore.add(mm.deserialize("<gray>id: " + PlayerContractManager.shortId(c.getId()) + "</gray>"));
        lore.add(mm.deserialize("<yellow>Нажмите, чтобы принять</yellow>"));

        meta.lore(lore);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, c.getId().toString());
        item.setItemMeta(meta);
        return item;
    }

    private String objectiveLabel(PlayerContract c) {
        if (c.getObjectiveType() == PlayerContractObjectiveType.CUSTOM) {
            return "Произвольная (сдать на проверку)";
        }
        return c.getObjectiveAmount() + "x " + c.getObjectiveTarget();
    }

    private String hoursLeft(PlayerContract c) {
        long minutes = java.time.Duration.between(java.time.Instant.now(), c.getDeadline()).toMinutes();
        if (minutes <= 0) return "истекает";
        if (minutes < 60) return minutes + " мин";
        return (minutes / 60) + " ч";
    }

    private ItemStack pane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material mat, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize(name));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) lore.add(mm.deserialize(line));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
