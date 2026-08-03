package me.lovelace.lovecontracts.gui;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.model.Contract;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hopper/9-slot confirmation menu adhering to gui-gen-4 Exception 1:
 * Slot 0: Glass | Slot 1: [✓] Confirm | Slot 2-3: Glass | Slot 4: Preview | Slot 5-6: Glass | Slot 7: [✗] Cancel | Slot 8: Glass
 */
public class ContractConfirmGUI implements Listener, InventoryHolder {

    private final LoveContracts plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, Contract> pendingContracts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    private static final ItemStack GLASS_PANE;
    private static final ItemStack CONFIRM_BUTTON;
    private static final ItemStack CANCEL_BUTTON;

    static {
        GLASS_PANE = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = GLASS_PANE.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        GLASS_PANE.setItemMeta(glassMeta);

        CONFIRM_BUTTON = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta confirmMeta = CONFIRM_BUTTON.getItemMeta();
        confirmMeta.displayName(MiniMessage.miniMessage().deserialize("<green>✓ Принять контракт</green>"));
        confirmMeta.lore(List.of(Component.empty(), MiniMessage.miniMessage().deserialize("<gray>Нажмите, чтобы подтвердить принятие</gray>")));
        CONFIRM_BUTTON.setItemMeta(confirmMeta);

        CANCEL_BUTTON = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta cancelMeta = CANCEL_BUTTON.getItemMeta();
        cancelMeta.displayName(MiniMessage.miniMessage().deserialize("<red>✗ Отмена</red>"));
        cancelMeta.lore(List.of(Component.empty(), MiniMessage.miniMessage().deserialize("<gray>Нажмите, чтобы вернуться на доску</gray>")));
        CANCEL_BUTTON.setItemMeta(cancelMeta);
    }

    public ContractConfirmGUI(LoveContracts plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Contract contract, ItemStack previewItem) {
        pendingContracts.put(player.getUniqueId(), contract);
        Inventory inv = Bukkit.createInventory(this, 9,
                mm.deserialize("<dark_gray>Подтверждение контракта</dark_gray>"));

        inv.setItem(0, GLASS_PANE);
        inv.setItem(1, CONFIRM_BUTTON);
        inv.setItem(2, GLASS_PANE);
        inv.setItem(3, GLASS_PANE);
        inv.setItem(4, previewItem != null ? previewItem : GLASS_PANE);
        inv.setItem(5, GLASS_PANE);
        inv.setItem(6, GLASS_PANE);
        inv.setItem(7, CANCEL_BUTTON);
        inv.setItem(8, GLASS_PANE);

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ContractConfirmGUI)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        long now = System.currentTimeMillis();
        long last = lastClick.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 300) return;
        lastClick.put(player.getUniqueId(), now);

        int slot = event.getRawSlot();
        if (slot < 0 || slot > 8) return;

        Contract contract = pendingContracts.get(player.getUniqueId());

        if (slot == 1) { // Confirm
            pendingContracts.remove(player.getUniqueId());
            player.closeInventory();
            if (contract != null) {
                plugin.getContractManager().acceptContract(player, contract.getId());
            }
            return;
        }

        if (slot == 7) { // Cancel
            pendingContracts.remove(player.getUniqueId());
            plugin.getContractGUI().open(player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ContractConfirmGUI) {
            UUID uuid = event.getPlayer().getUniqueId();
            pendingContracts.remove(uuid);
            lastClick.remove(uuid);
        }
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
