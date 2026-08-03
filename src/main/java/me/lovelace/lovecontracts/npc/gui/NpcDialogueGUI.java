package me.lovelace.lovecontracts.npc.gui;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.npc.model.NpcDialogueNode;
import me.lovelace.lovecontracts.npc.model.NpcDialogueOption;
import me.lovelace.lovecontracts.npc.model.NpcQuest;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 54-slot NPC Dialogue GUI conforming to gui-gen-4 v1.6 standards:
 * Slot 0: NPC Head | Slots 1-8: Glass
 * Row 1 (9-17): Glass divider
 * Working Zone (18-44): Dialogue Text on Slot 22, Option choices on Slots 30, 31, 32, 33, 34 (NO glass filler)
 * Footer (45-53): Glass, Slot 53 Close barrier
 */
public class NpcDialogueGUI implements Listener, InventoryHolder {

    private final LoveContracts plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();
    private final Map<UUID, NpcQuest> activeQuests = new ConcurrentHashMap<>();
    private final Map<UUID, NpcDialogueNode> activeNodes = new ConcurrentHashMap<>();

    private static final ItemStack GLASS_PANE;
    private static final ItemStack CLOSE_BUTTON;

    static {
        GLASS_PANE = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = GLASS_PANE.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        GLASS_PANE.setItemMeta(glassMeta);

        CLOSE_BUTTON = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = CLOSE_BUTTON.getItemMeta();
        closeMeta.displayName(MiniMessage.miniMessage().deserialize("<red>Закрыть</red>"));
        closeMeta.lore(List.of(Component.empty(), MiniMessage.miniMessage().deserialize("<gray>Закрыть разговор</gray>")));
        CLOSE_BUTTON.setItemMeta(closeMeta);
    }

    private static final int[] OPTION_SLOTS = { 29, 30, 31, 32, 33 };

    public NpcDialogueGUI(LoveContracts plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, NpcQuest quest, NpcDialogueNode node) {
        if (player == null || quest == null || node == null) return;

        UUID uuid = player.getUniqueId();
        activeQuests.put(uuid, quest);
        activeNodes.put(uuid, node);

        Inventory inv = Bukkit.createInventory(this, 54, mm.deserialize("<gold>" + quest.getDisplayName() + "</gold>"));

        // Header (0-8)
        inv.setItem(0, npcHeadItem(quest));
        for (int s = 1; s <= 8; s++) inv.setItem(s, GLASS_PANE);

        // Row 1 (9-17) - Pure glass divider
        for (int s = 9; s <= 17; s++) inv.setItem(s, GLASS_PANE);

        // Dialogue text on Slot 22
        inv.setItem(22, dialogueTextItem(node));

        // Options on OPTION_SLOTS (no glass filler!)
        List<NpcDialogueOption> options = node.getOptions();
        for (int i = 0; i < options.size() && i < OPTION_SLOTS.length; i++) {
            inv.setItem(OPTION_SLOTS[i], optionItem(options.get(i)));
        }

        // Footer (45-53)
        for (int s = 45; s <= 52; s++) inv.setItem(s, GLASS_PANE);
        inv.setItem(53, CLOSE_BUTTON);

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof NpcDialogueGUI)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        long now = System.currentTimeMillis();
        long last = lastClick.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 300) return;
        lastClick.put(player.getUniqueId(), now);

        int slot = event.getRawSlot();
        if (slot < 0 || slot > 53) return;

        if (slot == 53) {
            player.closeInventory();
            return;
        }

        UUID uuid = player.getUniqueId();
        NpcQuest quest = activeQuests.get(uuid);
        NpcDialogueNode node = activeNodes.get(uuid);
        if (quest == null || node == null) return;

        for (int i = 0; i < OPTION_SLOTS.length && i < node.getOptions().size(); i++) {
            if (slot == OPTION_SLOTS[i]) {
                NpcDialogueOption option = node.getOptions().get(i);
                handleOptionAction(player, quest, option);
                return;
            }
        }
    }

    private void handleOptionAction(Player player, NpcQuest quest, NpcDialogueOption option) {
        switch (option.getAction()) {
            case ACCEPT_QUEST -> {
                player.closeInventory();
                plugin.getNpcQuestManager().startQuest(player, quest);
            }
            case NEXT_NODE -> {
                if (option.getNextNode() != null) {
                    NpcDialogueNode next = quest.getDialogueNode(option.getNextNode());
                    if (next != null) {
                        open(player, quest, next);
                        return;
                    }
                }
                player.closeInventory();
            }
            case COMPLETE_STEP -> {
                player.closeInventory();
                plugin.getNpcQuestManager().advanceStep(player, quest);
            }
            case CLOSE -> player.closeInventory();
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof NpcDialogueGUI) {
            UUID uuid = event.getPlayer().getUniqueId();
            activeQuests.remove(uuid);
            activeNodes.remove(uuid);
            lastClick.remove(uuid);
        }
    }

    private ItemStack npcHeadItem(NpcQuest quest) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize("<gold>" + quest.getDisplayName() + "</gold>"));
        meta.lore(List.of(mm.deserialize("<gray>Разговор с NPC #" + quest.getNpcId() + "</gray>")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack dialogueTextItem(NpcDialogueNode node) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize("<yellow>Диалог:</yellow>"));

        List<Component> lore = new ArrayList<>();
        for (String line : node.getTextLines()) {
            lore.add(mm.deserialize(line));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack optionItem(NpcDialogueOption option) {
        Material mat = switch (option.getAction()) {
            case ACCEPT_QUEST -> Material.LIME_DYE;
            case NEXT_NODE -> Material.CYAN_DYE;
            case COMPLETE_STEP -> Material.ORANGE_DYE;
            case CLOSE -> Material.RED_DYE;
        };

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize(option.getText()));
        meta.lore(List.of(Component.empty(), mm.deserialize("<gray>Нажмите для выбора</gray>")));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
