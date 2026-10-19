package me.lovelace.lovecontracts.player.gui;

import me.lovelace.lovecontracts.util.Notify;
import me.lovelace.lovecontracts.util.CoinFormat;
import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.gui.LoreBlocks;
import me.lovelace.lovecontracts.gui.LoreBlocks.Click;
import me.lovelace.lovecontracts.gui.LoreBlocks.Hint;
import me.lovelace.lovecontracts.manager.MessageManager;
import me.lovelace.lovecontracts.player.manager.PlayerContractManager;
import me.lovelace.lovecontracts.player.model.PlayerContract;
import me.lovelace.lovecontracts.player.model.PlayerContractObjectiveType;
import me.lovelace.lovecontracts.player.model.PlayerContractStatus;
import me.lovelace.lovecontracts.textures.HeadTextures;
import me.lovelace.lovecontracts.util.HeadUtil;
import me.lovelace.lovecontracts.util.ItemText;
import me.lovelace.lovecontracts.util.LoreBar;
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

    static {
        GLASS_PANE = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = GLASS_PANE.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        GLASS_PANE.setItemMeta(glassMeta);
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
        inv.setItem(SLOT_CLOSE, closeButton());

        player.openInventory(inv);
        openInventories.add(player.getUniqueId());
    }

    /** Navigation has no lore (gui-gen-6, section 14): the name only. */
    private ItemStack closeButton() {
        return HeadUtil.createBase64Head(CLOSE_HEAD, plugin.getMessageManager().text("gui.close-button"), List.of());
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
                    Notify.important(player, CoinFormat.component(player, result.message()));
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

    /**
     * Own contract: the state is in the name, the role and the progress in the info section, the commands that move
     * it on in their own section, and the click that cancels or gives it up while it is still open.
     */
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

        MessageManager messages = plugin.getMessageManager();
        String shortId = PlayerContractManager.shortId(c.getId());
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String state = messages.text(statusKey(c.getStatus()));
        meta.displayName(ItemText.upright(mm.deserialize(messages.text("gui.item-name-state", Map.of(
                "name", "<gold>" + c.getDescription() + "</gold>", "state", state)))));

        List<String> lines = new ArrayList<>();
        lines.add(messages.text("gui.my-subtitle"));
        lines.add("");
        lines.add(messages.text("gui.section-info"));
        lines.add(messages.text("gui.my-role", Map.of(
                "role", messages.text(isCreator ? "gui.my-role-creator" : "gui.my-role-executor"))));
        if (c.getObjectiveType() == PlayerContractObjectiveType.KILL_ENTITY) {
            lines.add(messages.text("gui.my-progress", Map.of(
                    "current", String.valueOf(c.getObjectiveProgress()),
                    "required", String.valueOf(c.getObjectiveAmount()))));
            lines.add(messages.text("gui.progress-bar", Map.of(
                    "bar", LoreBar.bar(c.getObjectiveProgress(), c.getObjectiveAmount()))));
        }
        lines.add(messages.text("gui.my-id", Map.of("id", shortId)));

        List<String> commands = new ArrayList<>();
        if (c.getObjectiveType() == PlayerContractObjectiveType.DELIVER_ITEM && !isCreator
                && c.getStatus().name().equals("IN_PROGRESS")) {
            commands.add(messages.text("gui.my-command-turnin", Map.of("id", shortId)));
        }
        if (c.getObjectiveType() == PlayerContractObjectiveType.CUSTOM && !isCreator
                && c.getStatus().name().equals("IN_PROGRESS")) {
            commands.add(messages.text("gui.my-command-submit", Map.of("id", shortId)));
        }
        if (isCreator && c.getStatus().name().equals("PENDING_REVIEW")) {
            commands.add(messages.text("gui.my-command-review", Map.of("id", shortId)));
        }
        if (!commands.isEmpty()) {
            lines.add("");
            lines.add(messages.text("gui.section-commands"));
            lines.addAll(commands);
        }
        if (!c.getStatus().isTerminal()) {
            lines.addAll(LoreBlocks.clicks(messages::getRaw, Hint.of(Click.LEFT,
                    messages.text(isCreator ? "gui.action-cancel-own" : "gui.action-abandon"))));
        }

        List<Component> lore = new ArrayList<>(lines.size());
        for (String line : lines) {
            lore.add(mm.deserialize(line));
        }
        meta.lore(ItemText.upright(lore));
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, c.getId().toString());
        item.setItemMeta(meta);
        return item;
    }

    /** Message key of a status, which carries its colour: the name shows it after {@code | }. */
    private static String statusKey(PlayerContractStatus status) {
        return switch (status) {
            case OPEN -> "gui.my-status-open";
            case IN_PROGRESS -> "gui.my-status-in-progress";
            case PENDING_REVIEW -> "gui.my-status-pending-review";
            case COMPLETED -> "gui.my-status-completed";
            case ABANDONED, CANCELLED -> "gui.my-status-cancelled";
            case EXPIRED -> "gui.my-status-expired";
        };
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
