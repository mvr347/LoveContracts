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
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 54-slot Contract Board — gui-gen-4 v1.6 compliant.
 * Header: Slot 1 (Filter), Slot 3 (Sort), Slot 5 (Dynamic Action: Create / Complete / Cancel).
 * Pagination: Slot 36 (Prev Page), Slot 44 (Next Page).
 * Footer: Slot 53 (Close).
 */
public class ContractGUI implements Listener, InventoryHolder {    public enum FilterMode {
        ALL("Все"),
        EASY("Легкие"),
        MEDIUM("Средние"),
        HARD("Сложные"),
        AVAILABLE("Доступные"),
        ACCEPTED("Взятые"),
        COMPLETED("Выполненные"),
        FAILED("Проваленные");

        private final String display;
        FilterMode(String display) { this.display = display; }
        public String getDisplay() { return display; }
        public FilterMode next() {
            FilterMode[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    public enum SortMode {
        ALL("Все"),
        REWARD_HIGH("От Б до М"),
        REWARD_LOW("От М до Б"),
        DIFFICULTY("Сложность");

        private final String display;
        SortMode(String display) { this.display = display; }
        public String getDisplay() { return display; }
        public SortMode next() {
            SortMode[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    private final LoveContracts plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final NamespacedKey contractKey;
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();
    private final Set<UUID> openInventories = ConcurrentHashMap.newKeySet();

    private final Map<UUID, FilterMode> playerFilters = new ConcurrentHashMap<>();
    private final Map<UUID, SortMode> playerSorts = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerPages = new ConcurrentHashMap<>();

    private static final String CLOSE_HEAD_DEFAULT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWZkMjQwMDAwMmFkOWZiYmJkMDA2Njk0MWViNWIxYTM4NGFiOWIwZTQ4YTE3OGVlOTZlNGQxMjlhNTIwODY1NCJ9fX0=";
    private static final String SORT_HEAD_DEFAULT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjU3YzdlOTZhODAyYzI3MDgwYzdmODA1MzgxNDM2OGVhOTRkZjg2NDQ1OTEyMGU1MTU1NzE4YjUwM3MzZWQ3In19fQ==";
    private static final String TYPE_FILTER_HEAD_DEFAULT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGViODFlZjg5MDIzNzk2NTBiYTc5ZjQ1NzIzZDZiOWM4ODgzODhhMDBmYzRlMTkyZjM0NTRmZTE5Mzg4MmVlMSJ9fX0=";
    private static final String LOCKED_HEAD_DEFAULT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmM1MGUzZTYxNTBkMDdjY2EwOWVkNzA3YjI0NDA0M2M5NDM3ZGJkMWJlOThlZTA4YWUwMzQwY2NiNmQ1OGM4OSJ9fX0=";

    private static final String STARTER_HEAD_DEFAULT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjA2MGZmMmZlOTM2Y2Q5YTdmNDJkMWQ3MDMyNjgxYzYwOGE2MTRkMmU0MGQ0ZDE5NGRlZTk5NTQ1OTA0ZSJ9fX0=";
    private static final String EASY_HEAD_DEFAULT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWYyM2YxMTVjYjk1MjBkZDRkNGNiMjkxMjRkYWJhYzVlNjg0NGY5NmNjZTI0MWEzZWM5Y2E2ZjdhMjk2MjQ3In19fQ==";
    private static final String MEDIUM_HEAD_DEFAULT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjM3Y2FlNWM1MWViMTU1OGVhODI4ZjU4ZTBkZmY4ZTZiN2IwYjFhMTgzZDczN2VlY2Y3MTQ2NjE3NjEifX19";
    private static final String HARD_HEAD_DEFAULT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmRhOTExNDM3YjRlY2ZhYTNjMTg5NDE2MjIxN2MwMWI6OGE1NWM4OWJiMmY0ZDQ5MjczNDVjZTVjNzk0In19fQ==";

    private static final String CREATE_HEAD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM3VkZDIwYmU5MzUyMDk0OWU2Y2U3ODlkYzRmNDNlZmFlYjI4YzcxN2VlNmJmY2JiZTAyNzgwMTQyZjcxNiJ9fX0=";
    private static final String PREV_HEAD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODY1MmUyYjkzNmNhODAyNmJkMjg2NTFkN2M5ZjI4MTlkMmU5MjM2OTc3MzRkMThkZmRiMTM1NTBmOGZkYWQ1ZiJ9fX0=";
    private static final String NEXT_HEAD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTEyODVjZDRlZDRlYWIwOGVjN2QyN2IxYTA4M2FiMjVjOTMwZDg0MGIwNDM2MDhhZTc5MzFkOTc2Njg1NmQ3ZSJ9fX0=";
    private static final String ACTIVE_QUEST_HEAD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjA2MGZmMmZlOTM2Y2Q5YTdmNDJkMWQ3MDMyNjgxYzYwOGE2MTRkMmU0MGQ0ZDE5NGRlZTk5NTQ1OTA0ZSJ9fX0=";

    private static final ItemStack GLASS_PANE;
    private static final ItemStack CLOSE_BUTTON;

    static {
        GLASS_PANE = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = GLASS_PANE.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        GLASS_PANE.setItemMeta(glassMeta);

        CLOSE_BUTTON = me.lovelace.lovecontracts.util.HeadUtil.createBase64Head(
                CLOSE_HEAD_DEFAULT,
                "<red>Закрыть</red>",
                List.of(Component.empty(), MiniMessage.miniMessage().deserialize("<gray>Закрыть меню</gray>"))
        );
    }

    private static final int[] CONTRACT_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int SLOT_FILTER = 3;
    private static final int SLOT_SORT = 5;

    private static final int SLOT_PREV = 36;
    private static final int SLOT_NEXT = 44;
    private static final int SLOT_CREATE = 52;
    private static final int SLOT_CLOSE = 53;

    public ContractGUI(LoveContracts plugin) {
        this.plugin = plugin;
        this.contractKey = new NamespacedKey(plugin, "contract_id");
    }

    public void open(Player player) {
        if (player == null) return;
        if (plugin.getContractManager().isContractsDisabled(player)) {
            player.sendMessage(mm.deserialize("<red>Для вас доступ к контрактам отключен администратором.</red>"));
            return;
        }

        UUID uuid = player.getUniqueId();
        FilterMode filter = playerFilters.getOrDefault(uuid, FilterMode.ALL);
        SortMode sort = playerSorts.getOrDefault(uuid, SortMode.ALL);

        List<Contract> rawContracts = plugin.getContractManager().getActiveContracts();
        List<Contract> filtered = applyFilterAndSort(rawContracts, player, filter, sort);

        int total = filtered.size();
        int maxPages = (int) Math.ceil((double) total / CONTRACT_SLOTS.length);
        if (maxPages == 0) maxPages = 1;
        int page = playerPages.getOrDefault(uuid, 0);
        if (page >= maxPages) page = maxPages - 1;
        if (page < 0) page = 0;
        playerPages.put(uuid, page);

        Inventory inv;
        if (player.getOpenInventory() != null 
                && player.getOpenInventory().getTopInventory() != null 
                && player.getOpenInventory().getTopInventory().getHolder() instanceof ContractGUI) {
            inv = player.getOpenInventory().getTopInventory();
            inv.clear();
        } else {
            inv = Bukkit.createInventory(this, 54, mm.deserialize(
                    plugin.getConfig().getString("gui.title",
                            "<gradient:#55FF55:#55FFFF>Доска контрактов</gradient>")));
        }

        // Header (0-8) according to gui-gen-4 rules
        for (int s = 0; s <= 8; s++) {
            if (s == SLOT_FILTER) {
                inv.setItem(s, filterButton(filter));
            } else if (s == SLOT_SORT) {
                inv.setItem(s, sortButton(sort));
            } else {
                inv.setItem(s, GLASS_PANE);
            }
        }

        // Row 1 (9-17) - Pure glass divider
        for (int s = 9; s <= 17; s++) {
            inv.setItem(s, GLASS_PANE);
        }

        // Side border glass removed for working area (slots 18, 26, 27, 35, 36/44 when not active button)

        // Pagination buttons on slots 36 and 44
        if (page > 0) {
            inv.setItem(SLOT_PREV, prevPageButton(page + 1, maxPages));
        }

        if (page < maxPages - 1) {
            inv.setItem(SLOT_NEXT, nextPageButton(page + 1, maxPages));
        }

        // Working Zone (CONTRACT_SLOTS) - Paginated
        int startIndex = page * CONTRACT_SLOTS.length;
        int endIndex = Math.min(startIndex + CONTRACT_SLOTS.length, total);
        int idx = 0;
        for (int i = startIndex; i < endIndex; i++) {
            inv.setItem(CONTRACT_SLOTS[idx++], contractItem(filtered.get(i), player));
        }

        // Footer (45-53): 45-51 glass, 52 creation / active contract button, 53 close button
        for (int s = 45; s <= 51; s++) {
            inv.setItem(s, GLASS_PANE);
        }
        Contract activeContract = plugin.getContractManager().getActiveContract(uuid);
        if (activeContract != null) {
            inv.setItem(SLOT_CREATE, currentContractButton(player, activeContract));
        } else {
            inv.setItem(SLOT_CREATE, inactiveCreateButton());
        }
        inv.setItem(SLOT_CLOSE, closeButton());

        if (player.getOpenInventory().getTopInventory() != inv) {
            player.openInventory(inv);
        }
        openInventories.add(uuid);
    }

    private List<Contract> applyFilterAndSort(List<Contract> contracts, Player player, FilterMode filter, SortMode sort) {
        List<Contract> result = new ArrayList<>(contracts);
        UUID uuid = player.getUniqueId();

        Set<String> activeIds = plugin.getContractManager().getActiveContractIds(uuid);
        Set<String> completedIds = plugin.getContractManager().getCompletedTodayContractIds(uuid);
        Set<String> failedIds = plugin.getContractManager().getFailedContractIds(uuid);

        // Filtering
        switch (filter) {
            case EASY -> result.removeIf(c -> c.getDifficulty() != Difficulty.EASY && c.getDifficulty() != Difficulty.STARTER);
            case MEDIUM -> result.removeIf(c -> c.getDifficulty() != Difficulty.MEDIUM);
            case HARD -> result.removeIf(c -> c.getDifficulty() != Difficulty.HARD);
            case AVAILABLE -> result.removeIf(c -> activeIds.contains(c.getId()) || completedIds.contains(c.getId()) || failedIds.contains(c.getId()));
            case ACCEPTED -> result.removeIf(c -> !activeIds.contains(c.getId()));
            case COMPLETED -> result.removeIf(c -> !completedIds.contains(c.getId()));
            case FAILED -> result.removeIf(c -> !failedIds.contains(c.getId()));
            case ALL -> {}
        }

        // Status rank comparator: 1: Доступные, 2: Взятые, 3: Выполненные, 4: Проваленные
        Comparator<Contract> statusComparator = Comparator.comparingInt(c -> getStatusRank(c, activeIds, completedIds, failedIds));

        // Sorting
        switch (sort) {
            case ALL -> result.sort(statusComparator);
            case REWARD_HIGH -> result.sort(statusComparator.thenComparing((c1, c2) -> Double.compare(getContractRewardValue(c2), getContractRewardValue(c1))));
            case REWARD_LOW -> result.sort(statusComparator.thenComparing(Comparator.comparingDouble(this::getContractRewardValue)));
            case DIFFICULTY -> result.sort(statusComparator.thenComparingInt(c -> c.getDifficulty().ordinal()));
        }

        return result;
    }

    private int getStatusRank(Contract c, Set<String> activeIds, Set<String> completedIds, Set<String> failedIds) {
        String id = c.getId();
        if (activeIds.contains(id)) {
            return 2; // Взятые
        }
        if (completedIds.contains(id)) {
            return 3; // Выполненные
        }
        if (failedIds.contains(id)) {
            return 4; // Проваленные
        }
        return 1; // Доступные
    }

    private double getContractRewardValue(Contract c) {
        if (c == null || c.getRewards() == null) return 0.0;
        double total = 0.0;
        for (me.lovelace.lovecontracts.model.Reward r : c.getRewards()) {
            total += r.getAmount();
        }
        return total;
    }

    public void refresh(Player player) {
        if (!isOpen(player)) return;
        Bukkit.getScheduler().runTask(plugin, () -> open(player));
    }

    public boolean isOpen(Player player) {
        return openInventories.contains(player.getUniqueId());
    }

    @EventHandler
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ContractGUI) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ContractGUI)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        long now = System.currentTimeMillis();
        long last = lastClick.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 300) return;
        lastClick.put(player.getUniqueId(), now);

        int slot = event.getRawSlot();
        if (slot < 0 || slot > 53) return;

        UUID uuid = player.getUniqueId();

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        if (slot == SLOT_FILTER) {
            FilterMode nextFilter = playerFilters.getOrDefault(uuid, FilterMode.ALL).next();
            playerFilters.put(uuid, nextFilter);
            playerPages.put(uuid, 0);
            open(player);
            return;
        }

        if (slot == SLOT_SORT) {
            SortMode nextSort = playerSorts.getOrDefault(uuid, SortMode.ALL).next();
            playerSorts.put(uuid, nextSort);
            playerPages.put(uuid, 0);
            open(player);
            return;
        }

        if (slot == SLOT_CREATE) {
            Contract activeContract = plugin.getContractManager().getActiveContract(uuid);
            if (activeContract != null) {
                boolean isDone = activeContract.getCondition() != null && activeContract.getCondition().isCompleted(player);
                if (event.isRightClick()) {
                    plugin.getContractManager().cancelContract(player, activeContract);
                    open(player);
                } else if (event.isLeftClick()) {
                    if (isDone) {
                        plugin.getContractManager().completeContract(player, activeContract);
                        open(player);
                    } else {
                        String progress = activeContract.getCondition() != null ? activeContract.getCondition().getProgressString(player) : "";
                        player.sendMessage(mm.deserialize("<yellow>Контракт еще не выполнен. Прогресс: " + progress + "</yellow>"));
                    }
                }
            } else {
                player.sendMessage(mm.deserialize("<red>Создание контрактов временно недоступно (\"В ближайшее время\").</red>"));
            }
            return;
        }

        if (slot == SLOT_PREV) {
            int page = playerPages.getOrDefault(uuid, 0);
            if (page > 0) {
                playerPages.put(uuid, page - 1);
                open(player);
            }
            return;
        }

        if (slot == SLOT_NEXT) {
            int page = playerPages.getOrDefault(uuid, 0);
            playerPages.put(uuid, page + 1);
            open(player);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        String id = meta.getPersistentDataContainer().get(contractKey, PersistentDataType.STRING);
        if (id == null || id.isEmpty()) return;

        Contract contract = plugin.getRegistry().getContract(id);
        if (contract == null) {
            player.sendMessage(mm.deserialize("<red>Контракт не найден.</red>"));
            refresh(player);
            return;
        }

        // Completed or failed contract -> Non-clickable!
        boolean completedToday = plugin.getContractManager().getCompletedTodayContractIds(uuid).contains(contract.getId());
        boolean failed = plugin.getContractManager().getFailedContractIds(uuid).contains(contract.getId());
        if (completedToday || failed) {
            return;
        }

        // Active (taken) contract interaction
        boolean isThisActive = plugin.getContractManager().getActiveContractIds(uuid).contains(contract.getId());
        if (isThisActive) {
            boolean isDone = contract.getCondition() != null && contract.getCondition().isCompleted(player);
            if (event.isRightClick()) {
                plugin.getContractManager().cancelContract(player, contract);
                open(player);
            } else if (event.isLeftClick()) {
                if (isDone) {
                    plugin.getContractManager().completeContract(player, contract);
                    open(player);
                } else {
                    String progress = contract.getCondition() != null ? contract.getCondition().getProgressString(player) : "";
                    player.sendMessage(mm.deserialize("<yellow>Контракт еще не выполнен. Прогресс: " + progress + "</yellow>"));
                }
            }
            return;
        }

        // Check active contract limit (max 1 active contract)
        if (plugin.getContractManager().hasActiveContract(uuid)) {
            player.sendMessage(mm.deserialize("<red>Вы уже выполняете контракт! Одновременно можно взять только один контракт.</red>"));
            return;
        }

        // Check contract requirements
        if (contract.getRequirement() != null) {
            int completedCount = plugin.getContractManager().getCompletedContractsCount(uuid);
            if (!contract.getRequirement().isMet(player, completedCount)) {
                player.sendMessage(mm.deserialize("<red>Вы не соответствуете требованиям этого контракта!</red>"));
                return;
            }
        }

        // Open Confirmation Hopper Menu
        plugin.getConfirmGUI().open(player, contract, clicked.clone());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ContractGUI) {
            UUID uuid = event.getPlayer().getUniqueId();
            cleanupPlayer(uuid);
        }
    }

    public void cleanupPlayer(UUID uuid) {
        if (uuid == null) return;
        openInventories.remove(uuid);
        lastClick.remove(uuid);
        playerFilters.remove(uuid);
        playerSorts.remove(uuid);
        playerPages.remove(uuid);
    }

    private ItemStack closeButton() {
        String base64 = me.lovelace.lovecontracts.util.HeadUtil.getHeadTexture("close", CLOSE_HEAD_DEFAULT);
        return me.lovelace.lovecontracts.util.HeadUtil.createBase64Head(
                base64,
                plugin.getMessageManager().getRaw("gui.close-button", "<red>Закрыть</red>"),
                List.of(Component.empty(), plugin.getMessageManager().getComponent("gui.close-lore", "<gray>Закрыть меню</gray>"))
        );
    }

    private ItemStack inactiveCreateButton() {
        String base64 = me.lovelace.lovecontracts.util.HeadUtil.getHeadTexture("create-inactive",
                me.lovelace.lovecontracts.util.HeadUtil.getHeadTexture("create", CREATE_HEAD));
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getMessageManager().getComponent("gui.inactive-create-disabled", "<red><b>Временно отключено</b></red>"));
        lore.add(plugin.getMessageManager().getComponent("gui.inactive-create-lore", "<gray>В ближайшее время</gray>"));

        return me.lovelace.lovecontracts.util.HeadUtil.createBase64Head(
                base64,
                plugin.getMessageManager().getRaw("gui.inactive-create", "<gradient:#55FF55:#55FFFF><b>+ Создать контракт</b></gradient>"),
                lore
        );
    }

    private ItemStack currentContractButton(Player player, Contract c) {
        String base64 = me.lovelace.lovecontracts.util.HeadUtil.getHeadTexture("active-quest", ACTIVE_QUEST_HEAD);
        boolean isDone = c.getCondition() != null && c.getCondition().isCompleted(player);
        String progress = c.getCondition() != null ? c.getCondition().getProgressString(player) : "0/0";

        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<gold>" + strip(c.getDisplayName()) + "</gold>"));
        lore.add(mm.deserialize("<gray>Сложность:</gray> " + c.getDifficulty().getFormattedTag()));
        lore.add(mm.deserialize("<gray>" + c.getDescription() + "</gray>"));
        lore.add(Component.empty());
        lore.add(mm.deserialize("<yellow>Время:</yellow> <white>" + c.getFormattedDuration() + "</white>"));
        lore.add(mm.deserialize("<yellow>Прогресс:</yellow> <white>" + progress + "</white>"));
        lore.add(Component.empty());
        lore.add(mm.deserialize("<green>Награды:</green>"));
        c.getRewards().forEach(r ->
                lore.add(mm.deserialize("<gold>  + " + r.getDisplay() + "</gold>")));
        lore.add(Component.empty());
        if (isDone) {
            lore.add(plugin.getMessageManager().getComponent("gui.contract-status-active-done-lmb", "<green><b>ЛКМ: Сдать контракт (Забрать награду)</b></green>"));
            lore.add(plugin.getMessageManager().getComponent("gui.contract-status-active-done-rmb", "<red><b>ПКМ: Отменить контракт (Провал и штраф)</b></red>"));
        } else {
            lore.add(plugin.getMessageManager().getComponent("gui.contract-status-active-in-progress", "<yellow>✓ ВЗЯТЫЙ (В процессе)</yellow>"));
            lore.add(plugin.getMessageManager().getComponent("gui.contract-status-active-done-rmb", "<red><b>ПКМ: Отменить контракт (Провал и штраф)</b></red>"));
        }

        String itemTitle = plugin.getMessageManager().getRaw("gui.current-contract", "<gradient:#FFFF55:#FFAA00><b>Текущий контракт</b></gradient>");
        ItemStack item = me.lovelace.lovecontracts.util.HeadUtil.createBase64Head(base64, itemTitle, lore);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(contractKey, PersistentDataType.STRING, c.getId());
        item.setItemMeta(meta);
        return item;
    }

    private String strip(String input) {
        if (input == null) return "";
        return input.replaceAll("<[^>]+>", "");
    }

    private ItemStack prevPageButton(int page, int maxPages) {
        String base64 = me.lovelace.lovecontracts.util.HeadUtil.getHeadTexture("prev", PREV_HEAD);
        String pageInfo = plugin.getMessageManager().getRaw("gui.page-info", "<gray>Страница {PAGE} из {MAX_PAGES}</gray>")
                .replace("{PAGE}", String.valueOf(page))
                .replace("{MAX_PAGES}", String.valueOf(maxPages));
        return me.lovelace.lovecontracts.util.HeadUtil.createBase64Head(
                base64,
                plugin.getMessageManager().getRaw("gui.prev-page", "<yellow>← Предыдущая страница</yellow>"),
                List.of(mm.deserialize(pageInfo))
        );
    }

    private ItemStack nextPageButton(int page, int maxPages) {
        String base64 = me.lovelace.lovecontracts.util.HeadUtil.getHeadTexture("next", NEXT_HEAD);
        String pageInfo = plugin.getMessageManager().getRaw("gui.page-info", "<gray>Страница {PAGE} из {MAX_PAGES}</gray>")
                .replace("{PAGE}", String.valueOf(page))
                .replace("{MAX_PAGES}", String.valueOf(maxPages));
        return me.lovelace.lovecontracts.util.HeadUtil.createBase64Head(
                base64,
                plugin.getMessageManager().getRaw("gui.next-page", "<yellow>Следующая страница →</yellow>"),
                List.of(mm.deserialize(pageInfo))
        );
    }

    public ItemStack contractItem(Contract c, Player player) {
        UUID uuid = player.getUniqueId();
        boolean completedToday = plugin.getContractManager().getCompletedTodayContractIds(uuid).contains(c.getId());
        boolean failed = plugin.getContractManager().getFailedContractIds(uuid).contains(c.getId());
        boolean isThisActive = plugin.getContractManager().getActiveContractIds(uuid).contains(c.getId());
        boolean hasActive = plugin.getContractManager().hasActiveContract(uuid);

        if (completedToday) {
            String base64 = me.lovelace.lovecontracts.util.HeadUtil.getHeadTexture("completed",
                    me.lovelace.lovecontracts.util.HeadUtil.getHeadTexture("completed-contract",
                    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWY5M2VkM2YxOTY4NzYxMTNiMmU3NDYwOTMzNDkzYjgxZGE5MWI4ZjM0ZGIzYzUyODhhNjllZWI5NmRlNDBmYiJ9fX0="));

            List<Component> lore = new ArrayList<>();
            lore.add(mm.deserialize("<gray>Сложность:</gray> " + c.getDifficulty().getFormattedTag()));
            lore.add(mm.deserialize("<gray>" + c.getDescription() + "</gray>"));
            lore.add(Component.empty());
            lore.add(mm.deserialize("<green>Награды:</green>"));
            c.getRewards().forEach(r ->
                    lore.add(mm.deserialize("<gold>  + " + r.getDisplay() + "</gold>")));
            lore.add(Component.empty());
            lore.add(plugin.getMessageManager().getComponent("gui.contract-status-completed", "<green>✔ ВЫПОЛНЕН</green>"));

            ItemStack item = me.lovelace.lovecontracts.util.HeadUtil.createBase64Head(base64, c.getDisplayName() + " <green>(ВЫПОЛНЕН)</green>", lore);
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(contractKey, PersistentDataType.STRING, c.getId());
            item.setItemMeta(meta);
            return item;
        }

        if (failed) {
            String base64 = me.lovelace.lovecontracts.util.HeadUtil.getHeadTexture("failed",
                    me.lovelace.lovecontracts.util.HeadUtil.getHeadTexture("failed-contract",
                    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWY5M2VkM2YxOTY4NzYxMTNiMmU3NDYwOTMzNDkzYjgxZGE5MWI4ZjM0ZGIzYzUyODhhNjllZWI5NmRlNDBmYiJ9fX0="));

            List<Component> lore = new ArrayList<>();
            lore.add(mm.deserialize("<gray>Сложность:</gray> " + c.getDifficulty().getFormattedTag()));
            lore.add(mm.deserialize("<gray>" + c.getDescription() + "</gray>"));
            lore.add(Component.empty());
            lore.add(plugin.getMessageManager().getComponent("gui.contract-status-failed", "<red>✖ ПРОВАЛЕН</red>"));

            ItemStack item = me.lovelace.lovecontracts.util.HeadUtil.createBase64Head(base64, c.getDisplayName() + " <red>(ПРОВАЛЕН)</red>", lore);
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(contractKey, PersistentDataType.STRING, c.getId());
            item.setItemMeta(meta);
            return item;
        }

        if (isThisActive) {
            String base64 = me.lovelace.lovecontracts.util.HeadUtil.getHeadTexture("active",
                    me.lovelace.lovecontracts.util.HeadUtil.getHeadTexture("active-quest", ACTIVE_QUEST_HEAD));
            boolean isDone = c.getCondition() != null && c.getCondition().isCompleted(player);
            String progress = c.getCondition() != null ? c.getCondition().getProgressString(player) : "0/0";

            List<Component> lore = new ArrayList<>();
            lore.add(mm.deserialize("<gray>Сложность:</gray> " + c.getDifficulty().getFormattedTag()));
            lore.add(mm.deserialize("<gray>" + c.getDescription() + "</gray>"));
            lore.add(Component.empty());
            lore.add(mm.deserialize("<yellow>Время:</yellow> <white>" + c.getFormattedDuration() + "</white>"));
            lore.add(mm.deserialize("<yellow>Прогресс:</yellow> <white>" + progress + "</white>"));
            lore.add(Component.empty());
            lore.add(mm.deserialize("<green>Награды:</green>"));
            c.getRewards().forEach(r ->
                    lore.add(mm.deserialize("<gold>  + " + r.getDisplay() + "</gold>")));
            lore.add(Component.empty());
            if (isDone) {
                lore.add(plugin.getMessageManager().getComponent("gui.contract-status-active-done-lmb", "<green><b>ЛКМ: Сдать контракт (Забрать награду)</b></green>"));
                lore.add(plugin.getMessageManager().getComponent("gui.contract-status-active-done-rmb", "<red><b>ПКМ: Отменить контракт</b></red>"));
            } else {
                lore.add(plugin.getMessageManager().getComponent("gui.contract-status-active-in-progress", "<yellow>✓ ВЗЯТЫЙ (В процессе)</yellow>"));
                lore.add(plugin.getMessageManager().getComponent("gui.contract-status-active-done-rmb", "<red><b>ПКМ: Отменить контракт</b></red>"));
            }

            ItemStack item = me.lovelace.lovecontracts.util.HeadUtil.createBase64Head(base64, c.getDisplayName() + " <yellow>(ВЗЯТЫЙ)</yellow>", lore);
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(contractKey, PersistentDataType.STRING, c.getId());
            item.setItemMeta(meta);
            return item;
        }

        if (hasActive) {
            String base64 = me.lovelace.lovecontracts.util.HeadUtil.getHeadTexture("locked", LOCKED_HEAD_DEFAULT);

            List<Component> lore = new ArrayList<>();
            lore.add(mm.deserialize("<gray>Сложность:</gray> " + c.getDifficulty().getFormattedTag()));
            lore.add(mm.deserialize("<gray>" + c.getDescription() + "</gray>"));
            lore.add(Component.empty());
            lore.add(plugin.getMessageManager().getComponent("gui.contract-status-locked", "<red>🔒 КОНТРАКТ ЗАБЛОКИРОВАН</red>"));
            lore.add(plugin.getMessageManager().getComponent("gui.contract-status-locked-lore", "<red>У вас уже есть активный контракт!</red>"));

            ItemStack item = me.lovelace.lovecontracts.util.HeadUtil.createBase64Head(base64, "<red>" + c.getDisplayName() + "</red>", lore);
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(contractKey, PersistentDataType.STRING, c.getId());
            item.setItemMeta(meta);
            return item;
        }

        String base64Key = switch (c.getDifficulty()) {
            case STARTER -> "starter";
            case EASY -> "easy";
            case MEDIUM -> "medium";
            case HARD -> "hard";
        };
        String defaultHead = switch (c.getDifficulty()) {
            case STARTER -> STARTER_HEAD_DEFAULT;
            case EASY -> EASY_HEAD_DEFAULT;
            case MEDIUM -> MEDIUM_HEAD_DEFAULT;
            case HARD -> HARD_HEAD_DEFAULT;
        };
        String base64 = me.lovelace.lovecontracts.util.HeadUtil.getHeadTexture(base64Key, defaultHead);

        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<gray>Сложность:</gray> " + c.getDifficulty().getFormattedTag()));
        lore.add(mm.deserialize("<gray>" + c.getDescription() + "</gray>"));
        lore.add(Component.empty());
        lore.add(mm.deserialize("<yellow>Время:</yellow> <white>" + c.getFormattedDuration() + "</white>"));

        boolean reqMet = true;
        if (c.getRequirement() != null) {
            int completedCount = plugin.getContractManager().getCompletedContractsCount(uuid);
            reqMet = c.getRequirement().isMet(player, completedCount);
            lore.add(Component.empty());
            lore.add(mm.deserialize("<yellow>Требования:</yellow>"));
            for (String line : c.getRequirement().getMissingRequirementsLore(player, completedCount)) {
                lore.add(mm.deserialize(line));
            }
        }

        lore.add(Component.empty());
        lore.add(mm.deserialize("<green>Награды:</green>"));
        c.getRewards().forEach(r ->
                lore.add(mm.deserialize("<gold>  + " + r.getDisplay() + "</gold>")));

        if (!c.isStarter()) {
            boolean hasPenalty = c.getPenalties().stream()
                    .anyMatch(p -> p.getType() != me.lovelace.lovecontracts.model.Penalty.Type.NONE);
            if (hasPenalty) {
                lore.add(mm.deserialize("<red>Штрафы:</red>"));
                c.getPenalties().stream()
                        .filter(p -> p.getType() != me.lovelace.lovecontracts.model.Penalty.Type.NONE)
                        .forEach(p -> lore.add(mm.deserialize("<red>  - " + p.getDisplay() + "</red>")));
            }
        }

        lore.add(Component.empty());
        if (!reqMet) {
            lore.add(plugin.getMessageManager().getComponent("gui.contract-status-req-unmet", "<red>✗ ТРЕБОВАНИЯ НЕ ВЫПОЛНЕНЫ</red>"));
        } else {
            lore.add(plugin.getMessageManager().getComponent("gui.contract-status-click-to-accept", "<yellow>Нажмите, чтобы принять</yellow>"));
        }

        ItemStack item = me.lovelace.lovecontracts.util.HeadUtil.createBase64Head(base64, c.getDisplayName(), lore);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(contractKey, PersistentDataType.STRING, c.getId());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filterButton(FilterMode mode) {
        String base64 = me.lovelace.lovecontracts.util.HeadUtil.getHeadTexture("filter", TYPE_FILTER_HEAD_DEFAULT);
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getMessageManager().getComponent("gui.filter-current", "<gray>Текущий фильтр: <white>{FILTER}</white></gray>",
                java.util.Map.of("FILTER", mode.getDisplay())));
        lore.add(Component.empty());
        lore.add(plugin.getMessageManager().getComponent("gui.filter-click", "<gray>Нажмите для смены фильтра</gray>"));
        String title = plugin.getMessageManager().getRaw("gui.filter-button", "<gold>Фильтр:</gold> <yellow>{FILTER}</yellow>")
                .replace("{FILTER}", mode.getDisplay());
        return me.lovelace.lovecontracts.util.HeadUtil.createBase64Head(base64, title, lore);
    }

    private ItemStack sortButton(SortMode mode) {
        String base64 = me.lovelace.lovecontracts.util.HeadUtil.getHeadTexture("sort", SORT_HEAD_DEFAULT);
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getMessageManager().getComponent("gui.sort-current", "<gray>Текущая сортировка: <white>{SORT}</white></gray>",
                java.util.Map.of("SORT", mode.getDisplay())));
        lore.add(Component.empty());
        lore.add(plugin.getMessageManager().getComponent("gui.sort-click", "<gray>Нажмите для смены сортировки</gray>"));
        String title = plugin.getMessageManager().getRaw("gui.sort-button", "<gold>Сортировка:</gold> <yellow>{SORT}</yellow>")
                .replace("{SORT}", mode.getDisplay());
        return me.lovelace.lovecontracts.util.HeadUtil.createBase64Head(base64, title, lore);
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
