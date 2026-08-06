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
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 5-slot Hopper confirmation menu:
 * Slot 0: [✓] Confirm | Slot 1: Glass | Slot 2: Contract Preview | Slot 3: Glass | Slot 4: [✗] Cancel
 */
public class ContractConfirmGUI implements Listener, InventoryHolder {

    private final LoveContracts plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, Contract> pendingContracts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    private static final String YES_HEAD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTMwZjQ1MzdkMjE0ZDM4NjY2ZTYzMDRlOWM4NTFjZDZmN2U0MWEwZWI3YzI1MDQ5YzlkMjJjOGM1ZjY1NDVkZiJ9fX0=";
    private static final String NO_HEAD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWE2Nzg3YmEzMjU2NGU3YzJmM2EwY2U2NDQ5OGVjYmIyM2I4OTg0NWU1YTY2YjVjZWM3NzM2ZjcyOWVkMzcifX19";

    private static final ItemStack GLASS_PANE;
    private static final ItemStack CONFIRM_BUTTON;
    private static final ItemStack CANCEL_BUTTON;

    static {
        GLASS_PANE = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = GLASS_PANE.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        GLASS_PANE.setItemMeta(glassMeta);

        CONFIRM_BUTTON = me.lovelace.lovecontracts.util.HeadUtil.createBase64Head(
                me.lovelace.lovecontracts.util.HeadUtil.getHeadTexture("confirm", YES_HEAD),
                "<green>✓ Принять контракт</green>",
                List.of(Component.empty(), MiniMessage.miniMessage().deserialize("<gray>Нажмите, чтобы подтвердить принятие</gray>"))
        );

        CANCEL_BUTTON = me.lovelace.lovecontracts.util.HeadUtil.createBase64Head(
                me.lovelace.lovecontracts.util.HeadUtil.getHeadTexture("cancel", NO_HEAD),
                "<red>✗ Отмена</red>",
                List.of(Component.empty(), MiniMessage.miniMessage().deserialize("<gray>Нажмите, чтобы вернуться на доску</gray>"))
        );
    }

    public ContractConfirmGUI(LoveContracts plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Contract contract, ItemStack previewItem) {
        pendingContracts.put(player.getUniqueId(), contract);
        Component title = plugin.getMessageManager().getComponent("gui.confirm-title",
                "<gradient:#55FF55:#55FFFF>Подтверждение контракта</gradient>");
        Inventory inv = Bukkit.createInventory(this, InventoryType.HOPPER, title);

        ItemStack confirmBtn = me.lovelace.lovecontracts.util.HeadUtil.createBase64Head(
                me.lovelace.lovecontracts.util.HeadUtil.getHeadTexture("confirm", YES_HEAD),
                plugin.getMessageManager().getRaw("gui.confirm-yes", "<green>✓ Принять контракт</green>"),
                List.of(Component.empty(), plugin.getMessageManager().getComponent("gui.confirm-yes-lore", "<gray>Нажмите, чтобы подтвердить принятие</gray>"))
        );

        ItemStack cancelBtn = me.lovelace.lovecontracts.util.HeadUtil.createBase64Head(
                me.lovelace.lovecontracts.util.HeadUtil.getHeadTexture("cancel", NO_HEAD),
                plugin.getMessageManager().getRaw("gui.confirm-no", "<red>✗ Отмена</red>"),
                List.of(Component.empty(), plugin.getMessageManager().getComponent("gui.confirm-no-lore", "<gray>Нажмите, чтобы вернуться на доску</gray>"))
        );

        inv.setItem(0, confirmBtn);
        inv.setItem(1, GLASS_PANE);
        inv.setItem(2, previewItem != null ? previewItem : GLASS_PANE);
        inv.setItem(3, GLASS_PANE);
        inv.setItem(4, cancelBtn);

        player.openInventory(inv);
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
