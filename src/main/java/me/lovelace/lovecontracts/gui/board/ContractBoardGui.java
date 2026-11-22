package me.lovelace.lovecontracts.gui.board;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.gui.BoardModes.ChainFilter;
import me.lovelace.lovecontracts.gui.BoardModes.ChainSort;
import me.lovelace.lovecontracts.gui.BoardModes.MineFilter;
import me.lovelace.lovecontracts.gui.BoardModes.MineSort;
import me.lovelace.lovecontracts.gui.BoardModes.Mode;
import me.lovelace.lovecontracts.gui.BoardModes.OfferFilter;
import me.lovelace.lovecontracts.gui.BoardModes.OfferSort;
import me.lovelace.lovecontracts.gui.LoreBlocks;
import me.lovelace.lovecontracts.gui.LoreBlocks.Click;
import me.lovelace.lovecontracts.gui.LoreBlocks.Hint;
import me.lovelace.lovecontracts.model.Difficulty;
import me.lovelace.lovecontracts.model.Reward;
import me.lovelace.lovecontracts.npc.manager.NpcQuestManager;
import me.lovelace.lovecontracts.npc.model.ChainStatus;
import me.lovelace.lovecontracts.npc.model.ChainView;
import me.lovelace.lovecontracts.npc.model.NpcQuest;
import me.lovelace.lovecontracts.player.model.PlayerContract;
import me.lovelace.lovecontracts.player.model.PlayerContractStatus;
import me.lovelace.lovecontracts.textures.HeadTextures;
import me.lovelace.lovecontracts.util.CoinFormat;
import me.lovelace.lovecontracts.util.HeadUtil;
import me.lovelace.lovecontracts.util.Notify;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The one 54-slot contract board (gui_gen RULE 8). Header: slot 0 the NPC head, slots 2-7 the visible tabs plus the
 * filter and sort of the current tab; Row1 glass; content paged in the work zone; footer with Close (and extra when needed).
 */
public final class ContractBoardGui extends BoardGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ContractBoard board;
    private final ChainCards chainCards;
    private final PlayerCards playerCards;

    private BoardTab tab;
    /** Set when an NPC with several conversations opened the board: only these quests are listed. */
    private final Set<String> onlyChains;

    private ChainFilter chainFilter = ChainFilter.ALL;
    private ChainSort chainSort = ChainSort.DEFAULT;
    private OfferFilter offerFilter = OfferFilter.ALL;
    private OfferSort offerSort = OfferSort.NEWEST;
    private MineFilter mineFilter = MineFilter.ALL;
    private MineSort mineSort = MineSort.NEWEST;

    private List<PlayerContract> offers = List.of();
    private List<PlayerContract> mine = List.of();

    ContractBoardGui(ContractBoard board, LoveContracts plugin, Player viewer, BoardTab tab, Set<String> onlyChains) {
        super(plugin, viewer);
        this.board = board;
        this.tab = tab;
        this.onlyChains = onlyChains;
        this.chainCards = new ChainCards(plugin);
        this.playerCards = new PlayerCards(plugin);
    }

    @Override
    protected Component title() {
        return messages().getComponent("gui.title", "<gold>Доска поручений</gold>");
    }

    // ------------------------------------------------------------------ opening and refreshing

    void begin() {
        board.register(this);
        load(this::show);
    }

    boolean isShown() {
        return inventory != null && viewer.isOnline() && viewer.getOpenInventory().getTopInventory().getHolder() == this;
    }

    boolean showsPlayerContracts() {
        return tab == BoardTab.PLAYERS || tab == BoardTab.MINE;
    }

    void refresh() {
        load(() -> {
            if (isShown()) render();
        });
    }

    private void reopen() {
        board.register(this);
        load(() -> {
            if (!viewer.isOnline()) return;
            render();
            viewer.openInventory(inventory);
        });
    }

    private void load(Runnable then) {
        switch (tab) {
            case PLAYERS -> plugin.getPlayerContractManager().listOpenFor(viewer).thenAccept(list ->
                    Bukkit.getScheduler().runTask(plugin, () -> { offers = list; then.run(); }));
            case MINE -> plugin.getPlayerContractManager().listMine(viewer).thenAccept(list ->
                    Bukkit.getScheduler().runTask(plugin, () -> { mine = list; then.run(); }));
            default -> then.run();
        }
    }

    @Override
    public void onClose() {
        board.closed(this);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render() {
        frame();
        button(0, npcHeader(), null);
        controls(headerControls());
        switch (tab) {
            case QUESTS -> renderQuests();
            case PLAYERS -> renderPlayers();
            case MINE -> renderMine();
        }
        footer(null);
    }

    private List<Control> headerControls() {
        List<Control> controls = new ArrayList<>();
        List<BoardTab> availableTabs = new ArrayList<>();
        for (BoardTab t : BoardTab.values()) {
            if (board.tabAvailable(viewer, t)) availableTabs.add(t);
        }
        // When only QUESTS is available (player contracts hidden), showing filter + sort directly keeps the header clean.
        if (availableTabs.size() > 1) {
            for (BoardTab t : availableTabs) {
                controls.add(new Control(tabButton(t), e -> switchTab(t)));
            }
        }
        controls.add(new Control(filterButton(), e -> cycleFilter(e.isRightClick())));
        controls.add(new Control(sortButton(), e -> cycleSort(e.isRightClick())));
        return controls;
    }

    private void switchTab(BoardTab target) {
        if (target == tab || !board.tabAvailable(viewer, target)) return;
        tab = target;
        page = 0;
        refresh();
    }

    private ItemStack tabButton(BoardTab t) {
        boolean selected = t == tab;
        String label = t("gui.tab." + t.key());
        String name = selected
                ? t("gui.item-name-state", Map.of("name", label, "state", t("gui.tab-state-open")))
                : label;
        List<String> lines = new ArrayList<>();
        lines.add(t("gui.tab-subtitle"));
        lines.add("");
        lines.add(t("gui.tab." + t.key() + "-lore"));
        String count = tabCount(t);
        if (count != null) lines.add(t("gui.tab-count", Map.of("count", count)));
        if (!selected) {
            lines.addAll(LoreBlocks.clicks(messages()::getRaw, Hint.of(Click.LEFT, t("gui.action-open-tab"))));
        }
        String base64 = switch (t) {
            case QUESTS -> HeadUtil.getHeadTexture("tab-quests", HeadTextures.PRESET);
            case PLAYERS -> HeadUtil.getHeadTexture("tab-players", HeadTextures.COUNT);
            case MINE -> HeadUtil.getHeadTexture("tab-mine", HeadTextures.REWARD);
        };
        ItemStack item = head(base64, name, lines);
        if (selected) item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        return item;
    }

    private String tabCount(BoardTab t) {
        return switch (t) {
            case QUESTS -> String.valueOf(plugin.getNpcQuestManager().viewsFor(viewer).stream()
                    .filter(v -> v.status() == ChainStatus.AVAILABLE || v.status() == ChainStatus.ACTIVE).count());
            case PLAYERS -> t == tab ? String.valueOf(visibleOffers().size()) : null;
            case MINE -> t == tab ? String.valueOf(mine.size()) : null;
        };
    }

    // ------------------------------------------------------------------ filter and sort buttons

    private ItemStack filterButton() {
        String base64 = HeadUtil.getHeadTexture("filter", HeadTextures.FILTER);
        return switch (tab) {
            case QUESTS -> cycleButton(base64, t("gui.filter-title"), ChainFilter.values(), chainFilter);
            case PLAYERS -> cycleButton(base64, t("gui.filter-title"), OfferFilter.values(), offerFilter);
            case MINE -> cycleButton(base64, t("gui.filter-title"), MineFilter.values(), mineFilter);
        };
    }

    private ItemStack sortButton() {
        String base64 = HeadUtil.getHeadTexture("sort", HeadTextures.SORT);
        return switch (tab) {
            case QUESTS -> cycleButton(base64, t("gui.sort-title"), ChainSort.values(), chainSort);
            case PLAYERS -> cycleButton(base64, t("gui.sort-title"), OfferSort.values(), offerSort);
            case MINE -> cycleButton(base64, t("gui.sort-title"), MineSort.values(), mineSort);
        };
    }

    private <E extends Enum<E> & Mode> ItemStack cycleButton(String base64, String title, E[] options, E current) {
        List<String> names = new ArrayList<>(options.length);
        for (E option : options) names.add(messages().getRaw(option.messageKey(), option.fallback()));
        List<String> lines = new ArrayList<>();
        lines.add(t("gui.cycle-subtitle"));
        lines.add("");
        lines.add(t("gui.cycle-options-title"));
        for (int i = 0; i < names.size(); i++) {
            String key = i == current.ordinal() ? "gui.cycle-option-current" : "gui.cycle-option-other";
            lines.add(t(key, Map.of("name", names.get(i))));
        }
        lines.addAll(LoreBlocks.clicks(messages()::getRaw,
                Hint.of(Click.LEFT, t("gui.action-cycle-next")),
                Hint.of(Click.RIGHT, t("gui.action-cycle-previous"))));
        String name = t("gui.cycle-name", Map.of("name", title, "current", names.get(current.ordinal())));
        return head(base64, name, lines);
    }

    private void cycleFilter(boolean backwards) {
        switch (tab) {
            case QUESTS -> chainFilter = backwards ? chainFilter.prev() : chainFilter.next();
            case PLAYERS -> offerFilter = backwards ? offerFilter.prev() : offerFilter.next();
            case MINE -> mineFilter = backwards ? mineFilter.prev() : mineFilter.next();
        }
        page = 0;
        render();
    }

    private void cycleSort(boolean backwards) {
        switch (tab) {
            case QUESTS -> chainSort = backwards ? chainSort.prev() : chainSort.next();
            case PLAYERS -> offerSort = backwards ? offerSort.prev() : offerSort.next();
            case MINE -> mineSort = backwards ? mineSort.prev() : mineSort.next();
        }
        page = 0;
        render();
    }

    // ------------------------------------------------------------------ tab: unified quests

    private void renderQuests() {
        NpcQuestManager manager = plugin.getNpcQuestManager();
        boolean atLimit = manager.activeCount(viewer) >= NpcQuestManager.MAX_ACTIVE_CHAINS;
        List<ChainView> views = new ArrayList<>();
        for (ChainView view : manager.viewsFor(viewer)) {
            if (onlyChains != null && !onlyChains.contains(view.quest().getId())) continue;
            if (chainFilter.accepts(view)) views.add(view);
        }
        views.sort(chainOrder());
        paged(views, v -> chainCards.card(viewer, v, atLimit), (v, e) -> chainClick(v, e, atLimit), "gui.empty-hint-quests");
    }

    private Comparator<ChainView> chainOrder() {
        Comparator<ChainView> byStatus = Comparator.comparingInt(v -> switch (v.status()) {
            case ACTIVE -> 0;
            case AVAILABLE -> 1;
            case COOLDOWN -> 2;
            case LOCKED, LOADING -> 3;
            case DONE -> 4;
        });
        return switch (chainSort) {
            case DEFAULT -> byStatus;
            case DIFFICULTY -> byStatus.thenComparingInt(v -> tierRank(v.quest().getTier()));
            case REWARD_HIGH -> byStatus.thenComparing(Comparator.comparingDouble((ChainView v) -> rewardOf(v.quest())).reversed());
            case REWARD_LOW -> byStatus.thenComparingDouble(v -> rewardOf(v.quest()));
        };
    }

    private static int tierRank(Difficulty tier) {
        return switch (tier) {
            case STARTER, EASY -> 0;
            case MEDIUM -> 1;
            case HARD -> 2;
        };
    }

    private static double rewardOf(NpcQuest quest) {
        double total = 0;
        for (Reward reward : quest.getRewards()) total += reward.getAmount();
        return total;
    }

    private void chainClick(ChainView view, InventoryClickEvent event, boolean atLimit) {
        NpcQuest quest = view.quest();
        switch (ChainCards.kindOf(view, atLimit)) {
            case TAKE -> {
                if (event.isLeftClick() && !event.isShiftClick()) {
                    board.confirmTakeChain(viewer, quest, this::reopen, () -> {
                        plugin.getNpcQuestManager().startFromBoard(viewer, quest);
                        reopen();
                    });
                } else if (event.isRightClick() && !event.isShiftClick()) {
                    talkToGiver(quest);
                }
            }
            case ACTIVE -> {
                if (event.isShiftClick() && event.isRightClick()) {
                    board.confirmAbandon(viewer, quest.getDisplayName(),
                            List.of("gui.effect-abandon-progress", "gui.effect-abandon-chain"), () -> {
                                plugin.getNpcQuestManager().abandon(viewer, quest);
                                reopen();
                            }, this::reopen);
                } else if (event.isLeftClick()) {
                    talkToGiver(quest);
                }
            }
            case BLOCKED -> {
            }
        }
    }

    private void talkToGiver(NpcQuest quest) {
        NpcQuestManager manager = plugin.getNpcQuestManager();
        NpcQuestManager.Whereabouts spot = manager.whereabouts(viewer, quest);
        if (!spot.name().isBlank() || !spot.where().isBlank()) {
            viewer.sendMessage(messages().getComponent("messages.chain-where",
                    "<yellow>Заказчик: <white>{NAME}</white> <gray>({WHERE})</gray></yellow>",
                    Map.of("NAME", MM.escapeTags(spot.name().isBlank() ? "NPC" : spot.name()),
                            "WHERE", MM.escapeTags(spot.where().isBlank() ? "—" : spot.where()))));
        }
        if (plugin.getQuestSidebar() != null) plugin.getQuestSidebar().focus(viewer.getUniqueId(), "story:" + quest.getId());
        viewer.closeInventory();
        manager.openNpcInteraction(viewer, quest.getId());
    }

    // ------------------------------------------------------------------ tab: offers of other players

    private List<PlayerContract> visibleOffers() {
        List<PlayerContract> list = new ArrayList<>();
        for (PlayerContract c : offers) {
            if (c.getCreatorId().equals(viewer.getUniqueId())) continue;
            if (c.getStatus() != PlayerContractStatus.OPEN || c.getExecutorId() != null) continue;
            if (offerFilter.accepts(c.getObjectiveType())) list.add(c);
        }
        Comparator<PlayerContract> order = switch (offerSort) {
            case NEWEST -> Comparator.comparing(PlayerContract::getCreatedAt).reversed();
            case REWARD_HIGH -> Comparator.comparingLong(PlayerContract::getGoldReward).reversed();
            case REWARD_LOW -> Comparator.comparingLong(PlayerContract::getGoldReward);
            case DEADLINE -> Comparator.comparing(PlayerContract::getDeadline);
        };
        list.sort(order);
        return list;
    }

    private void renderPlayers() {
        paged(visibleOffers(), c -> playerCards.offerCard(viewer, c), this::offerClick, "gui.empty-hint-players");
        button(BoardLayout.extraSlot(inventory.getSize()), createHelpButton(), e -> sendCreateHelp());
    }

    private void offerClick(PlayerContract contract, InventoryClickEvent event) {
        plugin.getPlayerContractManager().accept(viewer, contract.getId()).thenAccept(result ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Notify.event(viewer, CoinFormat.component(viewer, result.message()));
                    if (isShown()) refresh();
                }));
    }

    private ItemStack createHelpButton() {
        List<String> lines = new ArrayList<>();
        lines.add(t("gui.create-own-subtitle"));
        lines.add("");
        lines.add(t("gui.create-own-lore"));
        lines.add(t("gui.create-own-command-deliver"));
        lines.add(t("gui.create-own-command-kill"));
        lines.add(t("gui.create-own-command-custom"));
        lines.addAll(LoreBlocks.clicks(messages()::getRaw, Hint.of(Click.LEFT, t("gui.action-create-own"))));
        return head(HeadUtil.getHeadTexture("create", HeadTextures.CREATE), t("gui.create-own"), lines);
    }

    private void sendCreateHelp() {
        viewer.closeInventory();
        for (String key : List.of("gui.create-own-usage-deliver", "gui.create-own-usage-kill", "gui.create-own-usage-custom")) {
            viewer.sendMessage(MM.deserialize(t(key)));
        }
    }

    // ------------------------------------------------------------------ tab: my player contracts

    private void renderMine() {
        List<PlayerContract> list = new ArrayList<>();
        for (PlayerContract c : mine) {
            boolean creator = c.getCreatorId().equals(viewer.getUniqueId());
            if (mineFilter.accepts(creator, c.getStatus())) list.add(c);
        }
        Comparator<PlayerContract> order = switch (mineSort) {
            case NEWEST -> Comparator.comparing(PlayerContract::getCreatedAt).reversed();
            case RUNNING_FIRST -> Comparator.<PlayerContract>comparingInt(c -> c.getStatus().isTerminal() ? 1 : 0)
                    .thenComparing(Comparator.comparing(PlayerContract::getCreatedAt).reversed());
        };
        list.sort(order);
        paged(list, c -> playerCards.mineCard(viewer, c), this::mineClick, "gui.empty-hint-mine");
    }

    private void mineClick(PlayerContract contract, InventoryClickEvent event) {
        if (!ContractBoard.cancellable(contract) || !event.isLeftClick()) return;
        board.confirmAbandon(viewer, "<gold>" + MM.escapeTags(contract.getDescription()) + "</gold>",
                List.of("gui.effect-abandon-progress"), () ->
                plugin.getPlayerContractManager().abandon(viewer, contract.getId()).thenAccept(result ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Notify.important(viewer, CoinFormat.component(viewer, result.message()));
                            reopen();
                        })), this::reopen);
    }
}
