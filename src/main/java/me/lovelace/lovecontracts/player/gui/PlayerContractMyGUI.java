package me.lovelace.lovecontracts.player.gui;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.player.manager.PlayerContractManager;
import me.lovelace.lovecontracts.player.model.PlayerContract;
import me.lovelace.lovecontracts.player.model.PlayerContractObjectiveType;
import me.lovelace.lovecontracts.textures.HeadTextures;
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

    private static final String CLOSE_HEAD = HeadTextures.CLOSE;

    private static final ItemStack GLASS_PANE;
    private static final ItemStack CLOSE_BUTTON;

    static {
        GLASS_PANE = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = GLASS_PANE.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        GLASS_PANE.setItemMeta(glassMeta);

        CLOSE_BUTTON = me.lovelace.lovecontracts.util.HeadUtil.createBase64Head(
                CLOSE_HEAD,
                "<red>Закрыть</red>",
                List.of(Component.empty(), MiniMessage.miniMessage().deserialize("<gray>Закрыть меню</gray>"))
        );
    }

    private static final int[] CONTRACT_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
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

        // Header (0-8)
        for (int s = 0; s <= 8; s++) inv.setItem(s, GLASS_PANE);

        // Row 1 (9-17) - Pure glass divider
        for (int s = 9; s <= 17; s++) inv.setItem(s, GLASS_PANE);

        int idx = 0;
        for (PlayerContract c : contracts) {
            if (idx >= CONTRACT_SLOTS.length) break;
            inv.setItem(CONTRACT_SLOTS[idx++], contractItem(c, player));
        }

        // Footer (45-53)
        for (int s = 45; s <= 52; s++) inv.setItem(s, GLASS_PANE);
        inv.setItem(SLOT_CLOSE, CLOSE_BUTTON);

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
            UUID uuid = event.getPlayer().getUniqueId();
            cleanupPlayer(uuid);
        }
    }

    public void cleanupPlayer(UUID uuid) {
        if (uuid == null) return;
        openInventories.remove(uuid);
        lastClick.remove(uuid);
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
        lore.add(mm.deserialize("<gray>Статус:</gray> <white>" + statusLabel(c.getStatus()) + "</white>"));
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

    private String statusLabel(me.lovelace.lovecontracts.player.model.PlayerContractStatus status) {
        return switch (status) {
            case OPEN -> "<yellow>ОТКРЫТ</yellow>";
            case IN_PROGRESS -> "<gold>В ВЫПОЛНЕНИИ</gold>";
            case PENDING_REVIEW -> "<light_purple>НА ПРОВЕРКЕ</light_purple>";
            case COMPLETED -> "<green>ВЫПОЛНЕН</green>";
            case ABANDONED, CANCELLED -> "<red>ОТМЕНЕН</red>";
            case EXPIRED -> "<gray>ИСТЕК</gray>";
        };
    }

    private ItemStack headItem(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(mm.deserialize("<aqua>" + player.getName() + "</aqua>"));
        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<gray>Мои контракты</gray>"));
        meta.lore(lore);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
