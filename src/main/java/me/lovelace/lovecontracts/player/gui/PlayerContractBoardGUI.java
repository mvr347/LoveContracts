package me.lovelace.lovecontracts.player.gui;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.player.event.PlayerContractAcceptedEvent;
import me.lovelace.lovecontracts.player.event.PlayerContractCreatedEvent;
import me.lovelace.lovecontracts.player.event.PlayerContractEndedEvent;
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
        String titleStr = plugin.getConfig().getString("player-contracts.board.title",
                "<gradient:#55FF55:#55FFFF>Заказы игроков</gradient>");
        Component title = plugin.getMessageManager() != null ?
                plugin.getMessageManager().getComponent("gui.player-board-title", titleStr) :
                mm.deserialize(titleStr);
        Inventory inv = Bukkit.createInventory(this, 54, title);

        // Header (0-8)
        for (int s = 0; s <= 8; s++) inv.setItem(s, GLASS_PANE);

        // Row 1 (9-17) - Pure glass divider
        for (int s = 9; s <= 17; s++) inv.setItem(s, GLASS_PANE);

        int idx = 0;
        List<PlayerContract> availableToAccept = contracts.stream()
                .filter(c -> !c.getCreatorId().equals(player.getUniqueId()))
                .filter(c -> c.getStatus() == me.lovelace.lovecontracts.player.model.PlayerContractStatus.OPEN)
                .filter(c -> c.getExecutorId() == null)
                .toList();
        for (PlayerContract c : availableToAccept) {
            if (idx >= CONTRACT_SLOTS.length) break;
            inv.setItem(CONTRACT_SLOTS[idx++], contractItem(c));
        }

        // Footer (45-53)
        for (int s = 45; s <= 51; s++) inv.setItem(s, GLASS_PANE);
        ItemStack closeBtn = me.lovelace.lovecontracts.util.HeadUtil.createBase64Head(
                CLOSE_HEAD,
                plugin.getMessageManager().getRaw("gui.close-button", "<red>Закрыть</red>"),
                List.of(Component.empty(), plugin.getMessageManager().getComponent("gui.close-lore", "<gray>Закрыть меню</gray>"))
        );
        inv.setItem(SLOT_CLOSE, closeBtn);

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
    public void onContractAccepted(PlayerContractAcceptedEvent event) {
        refreshOpenBoardGUIs();
    }

    @EventHandler
    public void onContractCreated(PlayerContractCreatedEvent event) {
        refreshOpenBoardGUIs();
    }

    @EventHandler
    public void onContractEnded(PlayerContractEndedEvent event) {
        refreshOpenBoardGUIs();
    }

    private void refreshOpenBoardGUIs() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (UUID uuid : new ArrayList<>(openInventories)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() instanceof PlayerContractBoardGUI) {
                    open(p);
                } else {
                    openInventories.remove(uuid);
                }
            }
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof PlayerContractBoardGUI) {
            UUID uuid = event.getPlayer().getUniqueId();
            cleanupPlayer(uuid);
        }
    }

    public void cleanupPlayer(UUID uuid) {
        if (uuid == null) return;
        openInventories.remove(uuid);
        lastClick.remove(uuid);
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

    private ItemStack headItem(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(mm.deserialize("<aqua>" + player.getName() + "</aqua>"));
        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<gray>Заказы игроков</gray>"));
        meta.lore(lore);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
