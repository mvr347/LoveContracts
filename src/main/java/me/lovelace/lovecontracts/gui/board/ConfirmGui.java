package me.lovelace.lovecontracts.gui.board;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.gui.ConfirmMenuLayout;
import me.lovelace.lovecontracts.textures.HeadTextures;
import me.lovelace.lovecontracts.util.CoinFormat;
import me.lovelace.lovecontracts.util.HeadUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The confirmation menu of every risky action (take a contract or a chain, give one up). With ItemsAdder and
 * PlaceholderAPI it is the server's 27-slot confirm menu (owner decision 2026-11-05): the lore of the two big buttons
 * carries what will happen. Without them it is the standard 9-slot confirmation window of gui_gen (exception 1):
 * {@code [С][✓][С][П][П][П][С][✗][С]}, the details again in the lore of the buttons.
 *
 * <p>Confirm closes the window and runs {@code onConfirm}; cancel runs {@code onCancel} (usually back to the menu the
 * action came from). Closing the window with Esc does neither.</p>
 */
public final class ConfirmGui implements BoardMenu {

    /**
     * @param yesName      MiniMessage name of the confirm button
     * @param yesLines     lore of the confirm button: what will happen, then the click line
     * @param noName       MiniMessage name of the cancel button
     * @param noLines      lore of the cancel button
     * @param onConfirm    runs after the window is closed
     * @param onCancel     runs when the player cancels
     */
    public record Spec(String yesName, List<String> yesLines, String noName, List<String> noLines,
                       Runnable onConfirm, Runnable onCancel) {
    }

    private static final int SMALL_SIZE = 9;
    private static final int SMALL_CONFIRM = 1;
    private static final int SMALL_CANCEL = 7;
    private static final int[] SMALL_GLASS = {0, 2, 6, 8};

    private final LoveContracts plugin;
    private final Player viewer;
    private final Spec spec;
    private final boolean large;
    private Inventory inventory;

    private ConfirmGui(LoveContracts plugin, Player viewer, Spec spec, boolean large) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.spec = spec;
        this.large = large;
    }

    public static void open(LoveContracts plugin, Player viewer, Spec spec) {
        if (ConfirmMenuLayout.available(viewer) && openLarge(plugin, viewer, spec)) return;
        ConfirmGui gui = new ConfirmGui(plugin, viewer, spec, false);
        Inventory inv = Bukkit.createInventory(gui, SMALL_SIZE, plugin.getMessageManager()
                .getComponent("gui.confirm-title", "<gold>Подтверждение</gold>"));
        gui.inventory = inv;
        ItemStack glass = glass();
        for (int slot : SMALL_GLASS) inv.setItem(slot, glass);
        inv.setItem(SMALL_CONFIRM, HeadUtil.createBase64Head(HeadUtil.getHeadTexture("confirm", HeadTextures.CONFIRM),
                spec.yesName(), components(viewer, spec.yesLines())));
        inv.setItem(SMALL_CANCEL, HeadUtil.createBase64Head(HeadUtil.getHeadTexture("cancel", HeadTextures.CANCEL),
                spec.noName(), components(viewer, spec.noLines())));
        viewer.openInventory(inv);
    }

    /** The 27-slot menu; false (nothing opened) when the invisible item is gone, so the caller opens the small one. */
    private static boolean openLarge(LoveContracts plugin, Player viewer, Spec spec) {
        MiniMessage mm = MiniMessage.miniMessage();
        ItemStack yes = ConfirmMenuLayout.button(mm.deserialize(spec.yesName()), components(viewer, spec.yesLines()));
        ItemStack no = ConfirmMenuLayout.button(mm.deserialize(spec.noName()), components(viewer, spec.noLines()));
        if (yes == null || no == null) return false;
        ConfirmGui gui = new ConfirmGui(plugin, viewer, spec, true);
        Inventory inv = Bukkit.createInventory(gui, ConfirmMenuLayout.SIZE, ConfirmMenuLayout.title(viewer));
        gui.inventory = inv;
        ConfirmMenuLayout.fill(inv, yes, no);
        viewer.openInventory(inv);
        return true;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;
        boolean confirm = large ? ConfirmMenuLayout.isConfirm(slot) : slot == SMALL_CONFIRM;
        boolean cancel = large ? ConfirmMenuLayout.isCancel(slot) : slot == SMALL_CANCEL;
        if (confirm) {
            viewer.closeInventory();
            spec.onConfirm().run();
        } else if (cancel) {
            spec.onCancel().run();
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    private static ItemStack glass() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return pane;
    }

    private static List<Component> components(Player viewer, List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(CoinFormat.component(viewer, line));
        return out;
    }
}
