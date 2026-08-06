package me.lovelace.lovecontracts.gui;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.condition.CatchFishCondition;
import me.lovelace.lovecontracts.condition.ContractCondition;
import me.lovelace.lovecontracts.condition.CraftItemCondition;
import me.lovelace.lovecontracts.condition.KillEntityCondition;
import me.lovelace.lovecontracts.condition.MineMaterialCondition;
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
        int moneyReward = 500;
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
            player.sendMessage(mm.deserialize("<red>Создание контрактов временно отключено (\"В ближайшее время\").</red>"));
        }
    }

    private ItemStack presetItem(PresetType preset) {
        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<gray>Выберите шаблон квеста</gray>"));
        lore.add(Component.empty());
        lore.add(mm.deserialize("<yellow>Клик: Сменить тип</yellow>"));

        return HeadUtil.createBase64Head(
                PRESET_HEAD,
                "<gold>Шаблон квеста:</gold> <yellow>" + preset.getDisplay() + "</yellow>",
                lore
        );
    }

    private ItemStack countItem(int count) {
        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<gray>Требуемое количество выполнения</gray>"));
        lore.add(Component.empty());
        lore.add(mm.deserialize("<yellow>Клик: 10 → 25 → 50 → 100</yellow>"));

        return HeadUtil.createBase64Head(
                COUNT_HEAD,
                "<gold>Количество:</gold> <yellow>" + count + "</yellow>",
                lore
        );
    }

    private ItemStack rewardItem(int reward) {
        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<gray>Выставить награду в виде монет</gray>"));
        lore.add(mm.deserialize("<gray>Игрок может переставить размер награды</gray>"));
        lore.add(Component.empty());
        lore.add(mm.deserialize("<yellow>Клик: 250$ → 500$ → 1000$ → 2500$</yellow>"));

        return HeadUtil.createBase64Head(
                REWARD_HEAD,
                "<gold>Награда:</gold> <yellow>" + reward + "$</yellow>",
                lore
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
            state.count = switch (state.count) {
                case 10 -> 25;
                case 25 -> 50;
                case 50 -> 100;
                default -> 10;
            };
            open(player);
            return;
        }

        if (slot == 14) { // Reward toggle (coins)
            state.moneyReward = switch (state.moneyReward) {
                case 250 -> 500;
                case 500 -> 1000;
                case 1000 -> 2500;
                default -> 250;
            };
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
                player.sendMessage(mm.deserialize("<red>Вы уже создали контракт! Допускается только один созданный контракт на игрока.</red>"));
                return;
            }

            String id = prefix + "_" + System.currentTimeMillis();
            String displayName = "<green>" + state.preset.getDisplay() + "</green>";
            String description = "Выполните задачу: " + state.preset.getDisplay() + " (" + state.count + ")";

            Contract contract = new Contract(
                    id, displayName, description, Difficulty.EASY,
                    ContractType.REPEATING, -1, 1, 10,
                    1440, false, true, List.of(Reward.money(state.moneyReward)), List.of()
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
            player.sendMessage(mm.deserialize("<green>✔ Контракт успешно создан и добавлен на доску!</green>"));
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
