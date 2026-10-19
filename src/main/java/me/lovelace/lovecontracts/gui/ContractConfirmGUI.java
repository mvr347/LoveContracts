package me.lovelace.lovecontracts.gui;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.gui.LoreBlocks.Click;
import me.lovelace.lovecontracts.gui.LoreBlocks.Hint;
import me.lovelace.lovecontracts.manager.MessageManager;
import me.lovelace.lovecontracts.model.Contract;
import me.lovelace.lovecontracts.textures.HeadTextures;
import me.lovelace.lovecontracts.util.CoinFormat;
import me.lovelace.lovecontracts.util.HeadUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
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
 * 5-slot Hopper confirmation menu:
 * Slot 0: Confirm | Slot 1: Glass | Slot 2: Contract Preview | Slot 3: Glass | Slot 4: Cancel
 */
public class ContractConfirmGUI implements Listener, InventoryHolder {

    private final LoveContracts plugin;
    private final Map<UUID, Contract> pendingContracts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    private static final String YES_HEAD = HeadTextures.CONFIRM;
    private static final String NO_HEAD = HeadTextures.CANCEL;

    private static final ItemStack GLASS_PANE;

    static {
        GLASS_PANE = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = GLASS_PANE.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        GLASS_PANE.setItemMeta(glassMeta);
    }

    public ContractConfirmGUI(LoveContracts plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Contract contract, ItemStack previewItem) {
        pendingContracts.put(player.getUniqueId(), contract);
        Component title = plugin.getMessageManager().getComponent("gui.confirm-title",
                "<gold>Подтверждение контракта</gold>");
        Inventory inv = Bukkit.createInventory(this, InventoryType.HOPPER, title);

        ItemStack confirmBtn = HeadUtil.createBase64Head(
                HeadUtil.getHeadTexture("confirm", YES_HEAD),
                messages().text("gui.confirm-yes"),
                components(player, acceptLines(player, contract)));

        ItemStack cancelBtn = HeadUtil.createBase64Head(
                HeadUtil.getHeadTexture("cancel", NO_HEAD),
                messages().text("gui.confirm-no"),
                components(player, cancelLines()));

        inv.setItem(0, confirmBtn);
        inv.setItem(1, GLASS_PANE);
        inv.setItem(2, previewItem != null ? previewItem : GLASS_PANE);
        inv.setItem(3, GLASS_PANE);
        inv.setItem(4, cancelBtn);

        player.openInventory(inv);
    }

    private MessageManager messages() {
        return plugin.getMessageManager();
    }

    /** What accepting does, what it needs and what it pays, then the click that confirms. */
    private List<String> acceptLines(Player player, Contract contract) {
        MessageManager messages = messages();
        boolean penalty = ContractLore.hasPenalty(contract);
        List<String> lines = new ArrayList<>();
        lines.add(messages.text("gui.confirm-subtitle"));
        lines.add("");
        lines.add(messages.text("gui.section-effects"));
        lines.add(messages.text("gui.effect-active"));
        lines.add(messages.text("gui.effect-single"));
        if (penalty) {
            lines.add(messages.text("gui.effect-penalty"));
        }
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
        lines.addAll(LoreBlocks.clicks(messages::getRaw, Hint.of(Click.LEFT, messages.text("gui.action-confirm"))));
        return lines;
    }

    private List<String> cancelLines() {
        MessageManager messages = messages();
        List<String> lines = new ArrayList<>();
        lines.add(messages.text("gui.confirm-no-subtitle"));
        lines.add("");
        lines.add(messages.text("gui.confirm-no-text"));
        lines.addAll(LoreBlocks.clicks(messages::getRaw,
                Hint.of(Click.RIGHT, messages.text("gui.action-back-board"))));
        return lines;
    }

    private static List<Component> components(Player player, List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(CoinFormat.component(player, line));
        }
        return out;
    }

    @EventHandler
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ContractConfirmGUI) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ContractConfirmGUI)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        long now = System.currentTimeMillis();
        long last = lastClick.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 300) return;
        lastClick.put(player.getUniqueId(), now);

        int slot = event.getRawSlot();
        if (slot < 0 || slot > 4) return;

        Contract contract = pendingContracts.get(player.getUniqueId());

        if (slot == 0) { // Confirm
            pendingContracts.remove(player.getUniqueId());
            player.closeInventory();
            if (contract != null) {
                plugin.getContractManager().acceptContract(player, contract.getId());
            }
            return;
        }

        if (slot == 4) { // Cancel
            pendingContracts.remove(player.getUniqueId());
            plugin.getContractGUI().open(player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ContractConfirmGUI) {
            UUID uuid = event.getPlayer().getUniqueId();
            cleanupPlayer(uuid);
        }
    }

    public void cleanupPlayer(UUID uuid) {
        if (uuid == null) return;
        pendingContracts.remove(uuid);
        lastClick.remove(uuid);
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
