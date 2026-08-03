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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 54-slot Contract Board — gui-gen v1.5 compliant.
 * Features 2 header controls: Filter (Slot 2) & Sort (Slot 3).
 * Max 1 active contract limit + Confirmation Hopper menu on accept.
 * RULE 8: no glass filler in working zone content cells (18-44).
 */
public class ContractGUI implements Listener, InventoryHolder {

    public enum FilterMode {
        ALL("Все"),
        ACCEPTED("Принятые"),
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
        NEWEST("Новые"),
        OLDEST("Старые"),
        EASY_TO_HARD("Сначала легкие"),
        HARD_TO_EASY("Сначала сложные");

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
        closeMeta.lore(List.of(Component.empty(), MiniMessage.miniMessage().deserialize("<gray>Закрыть меню</gray>")));
        CLOSE_BUTTON.setItemMeta(closeMeta);
    }

    private static final int[] CONTRACT_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int SLOT_HEAD = 0;
    private static final int SLOT_FILTER = 2;
    private static final int SLOT_SORT = 3;
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
        SortMode sort = playerSorts.getOrDefault(uuid, SortMode.NEWEST);

        List<Contract> rawContracts = plugin.getContractManager().getActiveContracts();
        List<Contract> filtered = applyFilterAndSort(rawContracts, player, filter, sort);

        Inventory inv = Bukkit.createInventory(this, 54, mm.deserialize(
                plugin.getConfig().getString("gui.title",
                        "<gradient:#55FF55:#55FFFF>Доска контрактов</gradient>")));

        // Header (0-8)
        inv.setItem(SLOT_HEAD, headItem(player));
        inv.setItem(1, GLASS_PANE);
        inv.setItem(SLOT_FILTER, filterButton(filter));
        inv.setItem(SLOT_SORT, sortButton(sort));
        for (int s = 4; s <= 8; s++) {
            inv.setItem(s, GLASS_PANE);
        }

        // Row 1 (9-17) - Pure glass divider
        for (int s = 9; s <= 17; s++) {
            inv.setItem(s, GLASS_PANE);
        }

        // Working Zone (18-44) - NO GLASS AT ALL
        int idx = 0;
        for (Contract c : filtered) {
            if (idx >= CONTRACT_SLOTS.length) break;
            inv.setItem(CONTRACT_SLOTS[idx++], contractItem(c, player));
        }

        // Footer (45-53)
        for (int s = 45; s <= 52; s++) {
            inv.setItem(s, GLASS_PANE);
        }
        inv.setItem(SLOT_CLOSE, CLOSE_BUTTON);

        player.openInventory(inv);
        openInventories.add(uuid);
    }

    private List<Contract> applyFilterAndSort(List<Contract> contracts, Player player, FilterMode filter, SortMode sort) {
        List<Contract> result = new ArrayList<>(contracts);
        UUID uuid = player.getUniqueId();

        // 1. Starter Progression Lock: If player hasn't completed 3 starter contracts, show ONLY starter contracts
        if (!plugin.getContractManager().hasCompletedStarterContracts(player)) {
            result.removeIf(c -> !c.isStarter());
        }

        // 2. Role / Permission Restriction: easy_only permission
        if (player.hasPermission("lovecontracts.limit.easy_only")) {
            result.removeIf(c -> c.getDifficulty() == Difficulty.MEDIUM || c.getDifficulty() == Difficulty.HARD);
        }

        Set<String> activeIds = plugin.getContractManager().getActiveContractIds(uuid);
        Set<String> completedIds = plugin.getContractManager().getCompletedTodayContractIds(uuid);
        Set<String> failedIds = plugin.getContractManager().getFailedContractIds(uuid);

        // Filtering
        switch (filter) {
            case ACCEPTED -> result.removeIf(c -> !activeIds.contains(c.getId()));
            case COMPLETED -> result.removeIf(c -> !completedIds.contains(c.getId()));
            case FAILED -> result.removeIf(c -> !failedIds.contains(c.getId()));
            case ALL -> {}
        }

        // Sorting
        switch (sort) {
            case NEWEST -> {} // Keep default order
            case OLDEST -> java.util.Collections.reverse(result);
            case EASY_TO_HARD -> result.sort(Comparator.comparingInt(c -> c.getDifficulty().ordinal()));
            case HARD_TO_EASY -> result.sort((c1, c2) -> Integer.compare(c2.getDifficulty().ordinal(), c1.getDifficulty().ordinal()));
        }

        return result;
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

        UUID uuid = player.getUniqueId();

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        if (slot == SLOT_FILTER) {
            FilterMode nextFilter = playerFilters.getOrDefault(uuid, FilterMode.ALL).next();
            playerFilters.put(uuid, nextFilter);
            open(player);
            return;
        }

        if (slot == SLOT_SORT) {
            SortMode nextSort = playerSorts.getOrDefault(uuid, SortMode.NEWEST).next();
            playerSorts.put(uuid, nextSort);
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

        boolean accepted = plugin.getContractManager().hasAcceptedToday(uuid, contract.getId());
        if (accepted) {
            player.sendMessage(mm.deserialize("<yellow>Вы уже приняли этот контракт сегодня.</yellow>"));
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
            openInventories.remove(uuid);
            lastClick.remove(uuid);
        }
    }

    private ItemStack contractItem(Contract c, Player player) {
        UUID uuid = player.getUniqueId();
        boolean hasActive = plugin.getContractManager().hasActiveContract(uuid);
        boolean isThisActive = plugin.getContractManager().getActiveContractIds(uuid).contains(c.getId());

        // Base64 Locked Head when player already has an active contract
        if (hasActive && !isThisActive) {
            String base64 = plugin.getConfig().getString("gui.locked-head-texture",
                    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzkxMzZlZTFjYzk5NDU3YTNjZjhhMmIzZWVjOWFiMzAyODlmNzg2ZWNpeDNhNWVjMjAyYWViNjZlYjEzNmFiIn19fQ==");

            List<Component> lore = new ArrayList<>();
            lore.add(mm.deserialize("<gray>" + c.getDescription() + "</gray>"));
            lore.add(Component.empty());
            lore.add(mm.deserialize("<red>🔒 КОНТРАКТ ЗАБЛОКИРОВАН</red>"));
            lore.add(mm.deserialize("<red>У вас уже есть активный контракт!</red>"));
            lore.add(mm.deserialize("<gray>Завершите текущий контракт, чтобы брать новые.</gray>"));

            return createBase64Head(base64, "<red>" + c.getDisplayName() + "</red>", lore);
        }

        Material mat = switch (c.getDifficulty()) {
            case STARTER -> Material.WOODEN_SWORD;
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
        lore.add(mm.deserialize("<yellow>Сложность:</yellow> " + colorDiff(c.getDifficulty())));
        lore.add(mm.deserialize("<yellow>Время:</yellow> <white>" + c.getFormattedDuration() + "</white>"));

        if (c.getCondition() != null) {
            lore.add(mm.deserialize("<yellow>Прогресс:</yellow> <white>" +
                    c.getCondition().getProgressString(player) + "</white>"));
        }

        // Requirements check
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

        // Display penalties if not starter quest
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
        boolean accepted = plugin.getContractManager().hasAcceptedToday(uuid, c.getId());
        if (accepted) {
            lore.add(mm.deserialize("<gold>✓ ПРИНЯТО</gold>"));
        } else if (!reqMet) {
            lore.add(mm.deserialize("<red>✗ ТРЕБОВАНИЯ НЕ ВЫПОЛНЕНЫ</red>"));
        } else {
            lore.add(mm.deserialize("<yellow>Нажмите, чтобы принять</yellow>"));
        }

        meta.lore(lore);
        meta.getPersistentDataContainer().set(contractKey, PersistentDataType.STRING, c.getId());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBase64Head(String base64Texture, String displayName, List<Component> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            try {
                com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "Locked");
                profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", base64Texture));
                meta.setPlayerProfile(profile);
            } catch (Exception ignored) {}
            meta.displayName(mm.deserialize(displayName));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String colorDiff(Difficulty d) {
        return switch (d) {
            case STARTER -> "<aqua>НАЧАЛЬНЫЙ</aqua>";
            case EASY -> "<green>ЛЕГКИЙ</green>";
            case MEDIUM -> "<yellow>СРЕДНИЙ</yellow>";
            case HARD -> "<red>СЛОЖНЫЙ</red>";
        };
    }

    private ItemStack filterButton(FilterMode mode) {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize("<gold>Фильтр:</gold> <yellow>" + mode.getDisplay() + "</yellow>"));
        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<gray>Текущий фильтр: <white>" + mode.getDisplay() + "</white></gray>"));
        lore.add(Component.empty());
        lore.add(mm.deserialize("<gray>Нажмите для смены фильтра</gray>"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack sortButton(SortMode mode) {
        ItemStack item = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize("<gold>Сортировка:</gold> <yellow>" + mode.getDisplay() + "</yellow>"));
        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<gray>Текущая сортировка: <white>" + mode.getDisplay() + "</white></gray>"));
        lore.add(Component.empty());
        lore.add(mm.deserialize("<gray>Нажмите для смены сортировки</gray>"));
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
        lore.add(mm.deserialize("<gray>Ваша доска контрактов</gray>"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
