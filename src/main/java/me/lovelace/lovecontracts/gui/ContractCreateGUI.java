package me.lovelace.lovecontracts.gui;

import me.lovelace.lovecontracts.util.Notify;
import me.lovelace.lovecontracts.util.CoinFormat;
import me.lovelace.lovecontracts.util.EconomyConfig;
import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.condition.CatchFishCondition;
import me.lovelace.lovecontracts.condition.ContractCondition;
import me.lovelace.lovecontracts.condition.CraftItemCondition;
import me.lovelace.lovecontracts.condition.KillEntityCondition;
import me.lovelace.lovecontracts.condition.MineMaterialCondition;
import me.lovelace.lovecontracts.gui.LoreBlocks.Click;
import me.lovelace.lovecontracts.gui.LoreBlocks.Hint;
import me.lovelace.lovecontracts.manager.MessageManager;
import me.lovelace.lovecontracts.model.Contract;
import me.lovelace.lovecontracts.model.ContractType;
import me.lovelace.lovecontracts.model.Difficulty;
import me.lovelace.lovecontracts.model.Reward;
import me.lovelace.lovecontracts.textures.HeadTextures;
import me.lovelace.lovecontracts.util.HeadUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
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
 * 27-slot Contract Creation Modal — gui-gen-4 standard.
 * Currently disabled: {@link #open(Player)} only tells the player so. The item texts follow gui-gen-6 for the day it is
 * enabled again.
 */
public class ContractCreateGUI implements Listener, InventoryHolder {

    public enum PresetType {
        KILL_ZOMBIE("Убить зомби", Material.ROTTEN_FLESH),
        KILL_SKELETON("Охота на скелетов", Material.BONE),
        MINE_IRON("Добыча железа", Material.RAW_IRON),
        MINE_DIAMOND("Добыча алмазов", Material.DIAMOND),
        CATCH_FISH("Рыболовство", Material.COD),
        CRAFT_BREAD("Крафт хлеба", Material.BREAD);

        private final String display;
        private final Material icon;

        PresetType(String display, Material icon) {
            this.display = display;
            this.icon = icon;
        }

        public String getDisplay() { return display; }
        public Material getIcon() { return icon; }
        public PresetType next() {
            PresetType[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    private static class CreationState {
        PresetType preset = PresetType.KILL_ZOMBIE;
        int count = 25;
        long moneyReward = 500;
    }

    private final LoveContracts plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, CreationState> playerStates = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    private static final String PRESET_HEAD = HeadTextures.PRESET;
    private static final String COUNT_HEAD = HeadTextures.COUNT;
    private static final String REWARD_HEAD = HeadTextures.REWARD;
    private static final String CREATE_HEAD = HeadTextures.CREATE;

    private static final String BACK_HEAD = HeadTextures.PAGINATION_PREVIOUS;
    private static final String CLOSE_HEAD = HeadTextures.CLOSE;

    private static final ItemStack GLASS_PANE;

    static {
        GLASS_PANE = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = GLASS_PANE.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        GLASS_PANE.setItemMeta(glassMeta);
    }

    public ContractCreateGUI(LoveContracts plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        if (player != null) {
            Notify.event(player, mm.deserialize("<red>Создание контрактов временно отключено (\"В ближайшее время\").</red>"));
        }
    }

    private MessageManager messages() {
        return plugin.getMessageManager();
    }

    private List<Component> components(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(mm.deserialize(line));
        }
        return out;
    }

    private static List<Component> components(Player viewer, List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(CoinFormat.component(viewer, line));
        }
        return out;
    }

    /** The next count of the wizard's count button (10 → 25 → 50 → 100 → 10), as in {@link #onClick}. */
    private static int nextCount(int count) {
        return switch (count) {
            case 10 -> 25;
            case 25 -> 50;
            case 50 -> 100;
            default -> 10;
        };
    }

    private ItemStack presetItem(PresetType preset) {
        MessageManager messages = messages();
        List<String> lines = new ArrayList<>();
        lines.add(messages.text("gui.create-preset-subtitle"));
        lines.add("");
        lines.add(messages.text("gui.create-preset-hint"));
        lines.add("");
        lines.add(messages.text("gui.section-after-click"));
        lines.add(messages.text("gui.create-preset-change", Map.of(
                "now", preset.getDisplay(), "next", preset.next().getDisplay())));
        lines.addAll(LoreBlocks.clicks(messages::getRaw,
                Hint.of(Click.LEFT, messages.text("gui.action-create-preset"))));

        return HeadUtil.createBase64Head(
                PRESET_HEAD,
                messages.text("gui.create-preset-name", Map.of("value", preset.getDisplay())),
                components(lines)
        );
    }

    private ItemStack countItem(int count) {
        MessageManager messages = messages();
        List<String> lines = new ArrayList<>();
        lines.add(messages.text("gui.create-count-subtitle"));
        lines.add("");
        lines.add(messages.text("gui.create-count-hint"));
        lines.add("");
        lines.add(messages.text("gui.section-after-click"));
        lines.add(messages.text("gui.create-count-change", Map.of(
                "now", String.valueOf(count), "next", String.valueOf(nextCount(count)))));
        lines.add("");
        lines.add(messages.text("gui.section-steps"));
        lines.add(messages.text("gui.create-count-steps", Map.of("steps", "10 <dark_gray>→ <white>25 <dark_gray>→ <white>50 <dark_gray>→ <white>100")));
        lines.addAll(LoreBlocks.clicks(messages::getRaw,
                Hint.of(Click.LEFT, messages.text("gui.action-create-count"))));

        return HeadUtil.createBase64Head(
                COUNT_HEAD,
                messages.text("gui.create-count-name", Map.of("value", String.valueOf(count))),
                components(lines)
        );
    }

    /** The next reward step, as in {@link #onClick}: the configured steps in a circle; an unknown value gives the first. */
    private static long nextStep(List<Long> steps, long current) {
        int at = steps.indexOf(current);
        return steps.get((at + 1) % steps.size());
    }

    private ItemStack rewardItem(Player viewer, long reward) {
        MessageManager messages = messages();
        List<Long> steps = EconomyConfig.rewardSteps(plugin);
        List<String> lines = new ArrayList<>();
        lines.add(messages.text("gui.create-reward-subtitle"));
        lines.add("");
        lines.add(messages.text("gui.create-reward-hint"));
        lines.add(messages.text("gui.create-reward-hint-2"));
        lines.add("");
        lines.add(messages.text("gui.section-after-click"));
        lines.add(messages.text("gui.create-reward-change", Map.of(
                "now", CoinFormat.format(reward), "next", CoinFormat.format(nextStep(steps, reward)))));
        lines.add("");
        lines.add(messages.text("gui.section-steps"));
        for (long step : steps) {
            lines.addAll(ContractLore.coinRows(List.of(CoinFormat.format(step))));
        }
        lines.addAll(LoreBlocks.clicks(messages::getRaw,
                Hint.of(Click.LEFT, messages.text("gui.action-create-reward"))));

        return HeadUtil.createBase64Head(
                REWARD_HEAD,
                messages.text("gui.create-reward-name"),
                components(viewer, lines)
        );
    }

    @EventHandler
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ContractCreateGUI) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ContractCreateGUI)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        long now = System.currentTimeMillis();
        long last = lastClick.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 300) return;
        lastClick.put(player.getUniqueId(), now);

        int slot = event.getRawSlot();
        if (slot < 0 || slot > 26) return;

        CreationState state = playerStates.computeIfAbsent(player.getUniqueId(), u -> new CreationState());

        if (slot == 10) { // Preset toggle
            state.preset = state.preset.next();
            open(player);
            return;
        }

        if (slot == 12) { // Count toggle
            state.count = nextCount(state.count);
            open(player);
            return;
        }

        if (slot == 14) { // Reward toggle (coins)
            // Cycle through the configured steps (economy.creation-reward-steps); unknown value → first step.
            List<Long> steps = EconomyConfig.rewardSteps(plugin);
            state.moneyReward = nextStep(steps, state.moneyReward);
            open(player);
            return;
        }

        if (slot == 18) { // Back button
            playerStates.remove(player.getUniqueId());
            plugin.getContractGUI().open(player);
            return;
        }

        if (slot == 26) { // Close button
            playerStates.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }

        if (slot == 16) { // Create action
            String prefix = "custom_" + player.getUniqueId().toString().substring(0, 8);
            boolean alreadyCreated = plugin.getContractManager().getActiveContracts().stream()
                    .anyMatch(c -> c.getId().startsWith(prefix));

            if (alreadyCreated) {
                Notify.event(player, mm.deserialize("<red>Вы уже создали контракт! Допускается только один созданный контракт на игрока.</red>"));
                return;
            }

            String id = prefix + "_" + System.currentTimeMillis();
            String displayName = "<green>" + state.preset.getDisplay() + "</green>";
            String description = "Выполните задачу: " + state.preset.getDisplay() + " (" + state.count + ")";

            Contract contract = new Contract(
                    id, displayName, description, Difficulty.EASY,
                    ContractType.REPEATING, -1, 1, 10,
                    1440, false, true, List.of(Reward.money((double) state.moneyReward)), List.of()
            );

            ContractCondition condition = switch (state.preset) {
                case KILL_ZOMBIE -> new KillEntityCondition(contract, EntityType.ZOMBIE, state.count);
                case KILL_SKELETON -> new KillEntityCondition(contract, EntityType.SKELETON, state.count);
                case MINE_IRON -> new MineMaterialCondition(contract, Material.IRON_ORE, state.count);
                case MINE_DIAMOND -> new MineMaterialCondition(contract, Material.DIAMOND_ORE, state.count);
                case CATCH_FISH -> new CatchFishCondition(contract, state.count);
                case CRAFT_BREAD -> new CraftItemCondition(contract, Material.BREAD, state.count);
            };

            contract.setCondition(condition);
            plugin.getContractManager().addActiveContract(contract);

            playerStates.remove(player.getUniqueId());
            Notify.event(player, mm.deserialize("<green>✔ Контракт успешно создан и добавлен на доску!</green>"));
            plugin.getContractGUI().open(player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ContractCreateGUI) {
            UUID uuid = event.getPlayer().getUniqueId();
            cleanupPlayer(uuid);
        }
    }

    public void cleanupPlayer(UUID uuid) {
        if (uuid == null) return;
        lastClick.remove(uuid);
        playerStates.remove(uuid);
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
