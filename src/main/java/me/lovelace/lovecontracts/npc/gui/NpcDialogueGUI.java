package me.lovelace.lovecontracts.npc.gui;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.gui.LoreBlocks;
import me.lovelace.lovecontracts.gui.LoreBlocks.Click;
import me.lovelace.lovecontracts.gui.LoreBlocks.Hint;
import me.lovelace.lovecontracts.manager.MessageManager;
import me.lovelace.lovecontracts.npc.model.NpcDialogueNode;
import me.lovelace.lovecontracts.npc.model.NpcDialogueOption;
import me.lovelace.lovecontracts.npc.model.NpcQuest;
import me.lovelace.lovecontracts.textures.HeadTextures;
import me.lovelace.lovecontracts.util.HeadUtil;
import me.lovelace.lovecontracts.util.ItemText;
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

    private final Map<UUID, dev.lovelace.lovecore.api.npc.NpcHandle> speakers = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> expectedSteps = new ConcurrentHashMap<>();
    private final Map<UUID, Inventory> views = new ConcurrentHashMap<>();

    private static final String CLOSE_HEAD = HeadTextures.CLOSE;

    private static final ItemStack GLASS_PANE;

    static {
        GLASS_PANE = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = GLASS_PANE.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        GLASS_PANE.setItemMeta(glassMeta);
    }

    private static final int[] OPTION_SLOTS = { 29, 30, 31, 32, 33 };

    public NpcDialogueGUI(LoveContracts plugin) {
        this.plugin = plugin;
    }

    private record OfferPage(List<NpcQuest> offers, int page) implements InventoryHolder {
        private OfferPage { offers = List.copyOf(offers); }
        @Override public Inventory getInventory() { return null; }
    }

    public void openOffers(Player player, List<NpcQuest> offers) {
        openOffers(player, offers, 0);
    }

    private void openOffers(Player player, List<NpcQuest> offers, int page) {
        if (offers.isEmpty()) return;
        page = Math.max(0, Math.min(page, (offers.size() - 1) / 45));
        Inventory inventory = Bukkit.createInventory(new OfferPage(offers, page), 54,
                mm.deserialize("<gold>Поручения</gold>"));
        for (int slot = 0; slot < 45 && page * 45 + slot < offers.size(); slot++) {
            NpcQuest quest = offers.get(page * 45 + slot);
            ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(ItemText.upright(mm.deserialize(quest.getDisplayName())));
            meta.lore(List.of(Component.text(plugin.getNpcQuestManager().isActive(player, quest) ? "Продолжить поручение" : "Обсудить новое поручение")));
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
        }
        if (page > 0) inventory.setItem(45, offerNavigation("Назад"));
        inventory.setItem(49, closeButton());
        if ((page + 1) * 45 < offers.size()) inventory.setItem(53, offerNavigation("Далее"));
        player.openInventory(inventory);
    }

    private ItemStack offerNavigation(String name) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    public void open(Player player, NpcQuest quest, NpcDialogueNode node, dev.lovelace.lovecore.api.npc.NpcHandle speaker, int expected) {
        if (player == null || quest == null || node == null) return;

        UUID uuid = player.getUniqueId();


        Inventory inv = Bukkit.createInventory(this, 54, mm.deserialize("<gold>" + quest.getDisplayName() + "</gold>"));

        // Header (0-8)
        for (int s = 0; s <= 8; s++) inv.setItem(s, GLASS_PANE);

        // Row 1 (9-17) - Pure glass divider
        for (int s = 9; s <= 17; s++) inv.setItem(s, GLASS_PANE);

        // Dialogue text on Slot 22
        inv.setItem(22, dialogueTextItem(node, quest, player));

        // Options on OPTION_SLOTS (no glass filler!)
        List<NpcDialogueOption> options = node.getOptions();
        for (int i = 0; i < options.size() && i < OPTION_SLOTS.length; i++) {
            inv.setItem(OPTION_SLOTS[i], optionItem(options.get(i)));
        }

        // Footer (45-53)
        for (int s = 45; s <= 52; s++) inv.setItem(s, GLASS_PANE);
        inv.setItem(53, closeButton());

        player.openInventory(inv);
        activeQuests.put(uuid, quest);
        activeNodes.put(uuid, node);
        speakers.put(uuid, speaker);
        expectedSteps.put(uuid, expected);
        views.put(uuid, inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof OfferPage page) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            int slot = event.getRawSlot();
            if (slot == 49) player.closeInventory();
            else if (slot == 45 && page.page() > 0) openOffers(player, page.offers(), page.page() - 1);
            else if (slot == 53 && (page.page() + 1) * 45 < page.offers().size()) openOffers(player, page.offers(), page.page() + 1);
            else if (slot >= 0 && slot < 45 && page.page() * 45 + slot < page.offers().size()) {
                player.closeInventory();
                plugin.getNpcQuestManager().openNpcInteraction(player, page.offers().get(page.page() * 45 + slot).getId());
            }
            return;
        }
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
        if (quest == null || node == null || views.get(uuid) != event.getInventory()) return;
        if (!plugin.getNpcQuestManager().canInteract(player, speakers.get(uuid))) {
            player.closeInventory();
            return;
        }

        for (int i = 0; i < OPTION_SLOTS.length && i < node.getOptions().size(); i++) {
            if (slot == OPTION_SLOTS[i]) {
                NpcDialogueOption option = node.getOptions().get(i);
                handleOptionAction(player, quest, option, speakers.get(uuid), expectedSteps.getOrDefault(uuid, -1));
                return;
            }
        }
    }

    private void handleOptionAction(Player player, NpcQuest quest, NpcDialogueOption option, dev.lovelace.lovecore.api.npc.NpcHandle speaker, int expected) {
        switch (option.getAction()) {
            case ACCEPT_QUEST -> {
                player.closeInventory();
                if (expected == -1) plugin.getNpcQuestManager().startQuest(player, quest);
            }
            case NEXT_NODE -> {
                if (option.getNextNode() != null) {
                    NpcDialogueNode next = quest.getDialogueNode(option.getNextNode());
                    if (next != null) {
                        open(player, quest, next, speaker, expected);
                        return;
                    }
                }
                player.closeInventory();
            }
            case COMPLETE_STEP -> {
                player.closeInventory();
                plugin.getNpcQuestManager().confirmStep(player, quest, expected, speaker);
            }
            case CLOSE -> player.closeInventory();
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof NpcDialogueGUI) {
            UUID uuid = event.getPlayer().getUniqueId();
            if (views.get(uuid) != event.getInventory()) return;
            views.remove(uuid);
            speakers.remove(uuid);
            expectedSteps.remove(uuid);
            activeQuests.remove(uuid);
            activeNodes.remove(uuid);
            lastClick.remove(uuid);
        }
    }

    @EventHandler
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getInventory().getHolder() == this || event.getInventory().getHolder() instanceof OfferPage)
            event.setCancelled(true);
    }

    private MessageManager messages() {
        return plugin.getMessageManager();
    }

    /** Navigation has no lore (gui-gen-6, section 14): the name only. */
    private ItemStack closeButton() {
        String base64 = CLOSE_HEAD;
        return HeadUtil.createBase64Head(base64, messages().text("gui.close-button"), List.of());
    }

    private List<Component> toLore(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(mm.deserialize(line));
        }
        return out;
    }

    private ItemStack dialogueTextItem(NpcDialogueNode node, NpcQuest quest, Player player) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ItemText.upright(mm.deserialize(messages().text("gui.npc-dialogue-name"))));

        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize(messages().text("gui.npc-dialogue-subtitle")));
        lore.add(Component.empty());
        for (String line : node.getTextLines()) {
            lore.add(mm.deserialize(line));
        }
        lore.add(Component.empty());
        lore.add(mm.deserialize("<gold>Награда за всю цепочку:</gold>"));
        for (var reward : quest.getRewards()) for (String line : reward.getDisplayLines())
            lore.add(mm.deserialize(me.lovelace.lovecontracts.util.CoinFormat.resolveGlyphs(player, line)));
        meta.lore(ItemText.upright(lore));
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

        MessageManager messages = messages();
        List<String> lines = new ArrayList<>();
        lines.add(messages.text("gui.npc-option-subtitle"));
        lines.addAll(LoreBlocks.clicks(messages::getRaw, Hint.of(Click.LEFT, messages.text("gui.action-npc-option"))));

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ItemText.upright(mm.deserialize(option.getText())));
        meta.lore(ItemText.upright(toLore(lines)));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
