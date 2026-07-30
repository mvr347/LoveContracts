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
 * Контракты игрока — как нанимателя, так и исполнителя. Клик = отменить (наниматель, OPEN/IN_PROGRESS)
 * или отказаться (исполнитель, IN_PROGRESS/PENDING_REVIEW). Сдача предметов/кастомных задач —
 * через команды {@code /pcontract turnin} и {@code /pcontract submit}, так как GUI-клик
 * недостаточно выразителен для проверки инвентаря.
 */
public class PlayerContractMyGUI implements Listener, InventoryHolder {

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
    private static final int SLOT_CLOSE = 53;

    public PlayerContractMyGUI(LoveContracts plugin, PlayerContractManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.idKey = new NamespacedKey(plugin, "player_contract_id");
    }

    public void open(Player player) {
        manager.listMine(player).thenAccept(contracts ->
                Bukkit.getScheduler().runTask(plugin, () -> render(player, contracts)));
    }

    private void render(Player player, List<PlayerContract> contracts) {
        Inventory inv = Bukkit.createInventory(this, 54,
                mm.deserialize("<gradient:#55FF55:#55FFFF>Мои контракты</gradient>"));

        ItemStack glass = pane();
        for (int s = 0; s <= 8; s++) inv.setItem(s, glass);
        for (int s = 45; s <= 53; s++) inv.setItem(s, glass);
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9, glass);
            inv.setItem(row * 9 + 8, glass);
        }

        int idx = 0;
        for (PlayerContract c : contracts) {
            if (idx >= CONTRACT_SLOTS.length) break;
            inv.setItem(CONTRACT_SLOTS[idx++], contractItem(c, player));
        }

        inv.setItem(SLOT_CLOSE, button(Material.BARRIER, "<red>Закрыть</red>"));
        player.openInventory(inv);
        openInventories.add(player.getUniqueId());
    }

    public boolean isOpen(Player player) {
        return openInventories.contains(player.getUniqueId());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PlayerContractMyGUI)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        long now = System.currentTimeMillis();
        long last = lastClick.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 300) return;
        lastClick.put(player.getUniqueId(), now);

        int slot = event.getRawSlot();
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String idStr = clicked.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (idStr == null) return;

        UUID contractId = UUID.fromString(idStr);
        manager.abandon(player, contractId).thenAccept(result ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(mm.deserialize(result.message()));
                    open(player);
                }));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof PlayerContractMyGUI) {
            openInventories.remove(event.getPlayer().getUniqueId());
        }
    }

    private ItemStack contractItem(PlayerContract c, Player viewer) {
        boolean isCreator = c.getCreatorId().equals(viewer.getUniqueId());
        Material mat = switch (c.getStatus()) {
            case OPEN -> Material.PAPER;
            case IN_PROGRESS -> Material.CHEST;
            case PENDING_REVIEW -> Material.WRITABLE_BOOK;
            case COMPLETED -> Material.EMERALD;
            case ABANDONED, CANCELLED -> Material.GRAY_DYE;
            case EXPIRED -> Material.REDSTONE;
        };

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize("<gold>" + c.getDescription() + "</gold>"));

        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<gray>Роль:</gray> <white>" + (isCreator ? "Наниматель" : "Исполнитель") + "</white>"));
        lore.add(mm.deserialize("<gray>Статус:</gray> <white>" + c.getStatus() + "</white>"));
        if (c.getObjectiveType() == PlayerContractObjectiveType.KILL_ENTITY) {
            lore.add(mm.deserialize("<gray>Прогресс:</gray> <white>" + c.getProgressString() + "</white>"));
        }
        lore.add(mm.deserialize("<gray>id: " + PlayerContractManager.shortId(c.getId()) + "</gray>"));
        lore.add(Component.empty());

        if (c.getObjectiveType() == PlayerContractObjectiveType.DELIVER_ITEM && !isCreator
                && c.getStatus().name().equals("IN_PROGRESS")) {
            lore.add(mm.deserialize("<yellow>/pcontract turnin " + PlayerContractManager.shortId(c.getId()) + "</yellow>"));
        }
        if (c.getObjectiveType() == PlayerContractObjectiveType.CUSTOM && !isCreator
                && c.getStatus().name().equals("IN_PROGRESS")) {
            lore.add(mm.deserialize("<yellow>/pcontract submit " + PlayerContractManager.shortId(c.getId()) + "</yellow>"));
        }
        if (isCreator && c.getStatus().name().equals("PENDING_REVIEW")) {
            lore.add(mm.deserialize("<yellow>/pcontract review " + PlayerContractManager.shortId(c.getId()) + " accept|reject</yellow>"));
        }
        if (!c.getStatus().isTerminal()) {
            lore.add(mm.deserialize("<red>Нажмите, чтобы " + (isCreator ? "отменить" : "отказаться") + "</red>"));
        }

        meta.lore(lore);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, c.getId().toString());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack pane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
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
