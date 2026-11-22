package me.lovelace.lovecontracts.gui.board;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.gui.ContractLore;
import me.lovelace.lovecontracts.gui.LoreBlocks;
import me.lovelace.lovecontracts.gui.LoreBlocks.Click;
import me.lovelace.lovecontracts.gui.LoreBlocks.Hint;
import me.lovelace.lovecontracts.manager.MessageManager;
import me.lovelace.lovecontracts.model.Contract;
import me.lovelace.lovecontracts.npc.manager.NpcQuestManager;
import me.lovelace.lovecontracts.npc.model.NpcQuest;
import me.lovelace.lovecontracts.player.event.PlayerContractAcceptedEvent;
import me.lovelace.lovecontracts.player.event.PlayerContractCreatedEvent;
import me.lovelace.lovecontracts.player.event.PlayerContractEndedEvent;
import me.lovelace.lovecontracts.player.model.PlayerContract;
import me.lovelace.lovecontracts.util.Notify;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one contract board: every way into the contracts (the {@code /contracts} and {@code /pcontract} commands, the board
 * NPC, signs, NPCs that offer several chains) opens {@link ContractBoardGui} on the right tab. This class tracks the open
 * boards so other code can refresh them, and builds the confirmation windows of the risky actions.
 */
public final class ContractBoard implements Listener {

    private final LoveContracts plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, ContractBoardGui> open = new ConcurrentHashMap<>();

    public ContractBoard(LoveContracts plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ opening

    /** The board on its first tab, the daily contracts. */
    public void open(Player player) {
        open(player, BoardTab.SYSTEM);
    }

    public void open(Player player, BoardTab tab) {
        launch(player, tab, null);
    }

    /** The chains tab limited to the given chains: what an NPC with several conversations offers. */
    public void openChains(Player player, Collection<String> questIds) {
        launch(player, BoardTab.CHAINS, questIds == null ? null : Set.copyOf(questIds));
    }

    private void launch(Player player, BoardTab tab, Set<String> onlyChains) {
        if (player == null || !player.isOnline()) return;
        // The board is shared, the permissions are not: the daily contracts and chains need lovecontracts.use (and the
        // admin toggle), the player-contract tabs need pcontracts.use. A tab the player may not use is never offered.
        BoardTab target = tab;
        if (!tabAvailable(player, target)) {
            target = null;
            for (BoardTab candidate : BoardTab.values()) {
                if (tabAvailable(player, candidate)) {
                    target = candidate;
                    break;
                }
            }
        }
        if (target == null) {
            if (plugin.getContractManager().isContractsDisabled(player)) {
                Notify.event(player, mm.deserialize("<red>Для вас доступ к контрактам отключен администратором.</red>"));
            }
            return;
        }
        long refusedFor = plugin.getNpcQuestManager().refusalLeft(player.getUniqueId());
        if (refusedFor > 0) {
            // the contractor has had enough of cancelled quests: the board and /contracts stay shut for a while
            Notify.event(player, mm.deserialize("<red>Контрактщик не станет с вами работать ещё "
                    + DurationText.format(refusedFor) + ": вы слишком часто отказывались от поручений.</red>"));
            return;
        }
        new ContractBoardGui(this, plugin, player, target, target == BoardTab.CHAINS ? onlyChains : null).begin();
    }

    /** Whether the player may see and use a tab: each tab keeps the permission boundary of the menu it replaced. */
    public boolean tabAvailable(Player player, BoardTab tab) {
        return switch (tab) {
            case SYSTEM, CHAINS -> player.hasPermission("lovecontracts.use")
                    && !plugin.getContractManager().isContractsDisabled(player);
            case PLAYERS, MINE -> playerContractsVisible() && player.hasPermission("pcontracts.use");
        };
    }

    /**
     * Whether the player-to-player contract UI (tabs, /pcontract) is shown. Data, escrow refunds and payouts keep
     * working when it is hidden, only the entry points disappear.
     */
    public boolean playerContractsVisible() {
        return plugin.getConfig().getBoolean("player-contracts.ui-enabled", false);
    }

    // ------------------------------------------------------------------ tracking

    void register(ContractBoardGui gui) {
        open.put(gui.viewer().getUniqueId(), gui);
    }

    void closed(ContractBoardGui gui) {
        open.remove(gui.viewer().getUniqueId(), gui);
    }

    public boolean isOpen(Player player) {
        ContractBoardGui gui = open.get(player.getUniqueId());
        return gui != null && gui.isShown();
    }

    /** Redraws the player's board in place (a contract changed, a rotation happened); no-op when it is not open. */
    public void refresh(Player player) {
        if (player == null) return;
        ContractBoardGui gui = open.get(player.getUniqueId());
        if (gui == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (gui.isShown()) gui.refresh();
        });
    }

    public void cleanupPlayer(UUID uuid) {
        if (uuid != null) open.remove(uuid);
    }

    @EventHandler
    public void onContractAccepted(PlayerContractAcceptedEvent event) {
        refreshPlayerTabs();
    }

    @EventHandler
    public void onContractCreated(PlayerContractCreatedEvent event) {
        refreshPlayerTabs();
    }

    @EventHandler
    public void onContractEnded(PlayerContractEndedEvent event) {
        refreshPlayerTabs();
    }

    private void refreshPlayerTabs() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (ContractBoardGui gui : new ArrayList<>(open.values())) {
                if (!gui.viewer().isOnline() || !gui.isShown()) {
                    open.remove(gui.viewer().getUniqueId(), gui);
                } else if (gui.showsPlayerContracts()) {
                    gui.refresh();
                }
            }
        });
    }

    // ------------------------------------------------------------------ confirmations

    private MessageManager messages() {
        return plugin.getMessageManager();
    }

    private LoreBlocks.Style style() {
        return messages()::getRaw;
    }

    private List<String> noLines() {
        MessageManager messages = messages();
        List<String> lines = new ArrayList<>();
        lines.add(messages.text("gui.confirm-no-subtitle"));
        lines.add("");
        lines.add(messages.text("gui.confirm-no-text"));
        lines.addAll(LoreBlocks.clicks(style(), Hint.of(Click.RIGHT, messages.text("gui.action-back-board"))));
        return lines;
    }

    private void confirm(Player player, String yesName, List<String> yesLines, Runnable onConfirm, Runnable onCancel) {
        ConfirmGui.open(plugin, player, new ConfirmGui.Spec(yesName, yesLines,
                messages().text("gui.confirm-no"), noLines(), onConfirm, onCancel));
    }

    /** A confirmation with the caller's own texts (the builder's "delete this chain"). */
    public void confirmCustom(Player player, String yesName, List<String> yesLines, Runnable onConfirm, Runnable onCancel) {
        confirm(player, yesName, yesLines, onConfirm, onCancel);
    }

    /** Taking a daily contract: what it does, what it needs and what it pays. */
    public void confirmAccept(Player player, Contract contract, Runnable onCancel) {
        MessageManager messages = messages();
        boolean penalty = ContractLore.hasPenalty(contract);
        List<String> lines = new ArrayList<>();
        lines.add(messages.text("gui.confirm-subtitle"));
        lines.add("  " + contract.getDisplayName());
        lines.add("");
        lines.add(messages.text("gui.section-effects"));
        lines.add(messages.text("gui.effect-active"));
        lines.add(messages.text("gui.effect-single"));
        if (penalty) lines.add(messages.text("gui.effect-penalty"));
        if (contract.getRequirement() != null) {
            int completed = plugin.getContractManager().getCompletedContractsCount(player.getUniqueId());
            lines.add("");
            lines.add(messages.text("gui.section-requirements"));
            lines.addAll(new ContractLore(messages).requirementRows(player, contract.getRequirement(), completed));
        }
        lines.add("");
        lines.add(messages.text("gui.section-reward"));
        lines.addAll(ContractLore.coinRows(ContractLore.rewardLines(contract)));
        if (penalty) {
            lines.add("");
            lines.add(messages.text("gui.section-penalty"));
            lines.addAll(ContractLore.coinRows(ContractLore.penaltyLines(contract)));
        }
        lines.addAll(LoreBlocks.clicks(style(), Hint.of(Click.LEFT, messages.text("gui.action-confirm"))));
        confirm(player, messages.text("gui.confirm-yes"), lines,
                () -> plugin.getContractManager().acceptContract(player, contract.getId()), onCancel);
    }

    /** Taking an NPC chain from the board. */
    public void confirmTakeChain(Player player, NpcQuest quest, Runnable onCancel, Runnable onConfirm) {
        MessageManager messages = messages();
        List<String> lines = new ArrayList<>();
        lines.add(messages.text("gui.confirm-subtitle"));
        lines.add("  " + quest.getDisplayName());
        lines.add("");
        lines.add(messages.text("gui.section-effects"));
        lines.add(messages.text("gui.effect-chain-active"));
        lines.add(messages.text("gui.effect-chain-limit", Map.of("max", String.valueOf(NpcQuestManager.MAX_ACTIVE_CHAINS))));
        lines.add("");
        lines.add(messages.text("gui.section-reward"));
        lines.addAll(ContractLore.coinRows(ContractLore.rewardLines(quest.getRewards())));
        lines.addAll(LoreBlocks.clicks(style(), Hint.of(Click.LEFT, messages.text("gui.action-confirm"))));
        confirm(player, messages.text("gui.confirm-yes-chain"), lines, onConfirm, onCancel);
    }

    /**
     * Giving something up (a daily contract, a chain, a player contract): the window says what is lost. {@code losses}
     * are the effect rows ({@code gui.effect-*} keys) that apply to this case.
     */
    public void confirmAbandon(Player player, String targetName, List<String> losses, Runnable onConfirm, Runnable onCancel) {
        MessageManager messages = messages();
        List<String> lines = new ArrayList<>();
        lines.add(messages.text("gui.confirm-abandon-subtitle"));
        lines.add("  " + targetName);
        lines.add("");
        lines.add(messages.text("gui.section-effects"));
        for (String key : losses) lines.add(messages.text(key));
        lines.addAll(LoreBlocks.clicks(style(), Hint.of(Click.LEFT, messages.text("gui.action-confirm-abandon"))));
        confirm(player, messages.text("gui.confirm-yes-abandon"), lines, onConfirm, onCancel);
    }

    /** Whether a player contract is one the viewer may still cancel or give up. */
    static boolean cancellable(PlayerContract contract) {
        return !contract.getStatus().isTerminal();
    }
}
