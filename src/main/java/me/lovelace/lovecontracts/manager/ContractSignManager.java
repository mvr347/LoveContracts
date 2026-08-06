package me.lovelace.lovecontracts.manager;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.model.Contract;
import me.lovelace.lovecontracts.model.Difficulty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ContractSignManager {

    private final LoveContracts plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<Location, Integer> boundSigns = new ConcurrentHashMap<>();
    private File configFile;
    private YamlConfiguration config;

    public ContractSignManager(LoveContracts plugin) {
        this.plugin = plugin;
        load();
    }

    public synchronized void load() {
        boundSigns.clear();
        configFile = new File(plugin.getDataFolder(), "signs.yml");
        if (!configFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                configFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not create signs.yml", e);
            }
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        List<Map<?, ?>> list = config.getMapList("signs");
        for (Map<?, ?> map : list) {
            try {
                String worldName = (String) map.get("world");
                int x = ((Number) map.get("x")).intValue();
                int y = ((Number) map.get("y")).intValue();
                int z = ((Number) map.get("z")).intValue();
                int slot = ((Number) map.get("slot")).intValue();

                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    Location loc = new Location(world, x, y, z);
                    boundSigns.put(loc, slot);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to parse bound sign entry: " + e.getMessage());
            }
        }
    }

    public synchronized void save() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<Location, Integer> entry : boundSigns.entrySet()) {
            Location loc = entry.getKey();
            if (loc.getWorld() == null) continue;
            Map<String, Object> map = new ConcurrentHashMap<>();
            map.put("world", loc.getWorld().getName());
            map.put("x", loc.getBlockX());
            map.put("y", loc.getBlockY());
            map.put("z", loc.getBlockZ());
            map.put("slot", entry.getValue());
            list.add(map);
        }
        config.set("signs", list);
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save signs.yml", e);
        }
    }

    public int getNextAvailableSlot() {
        if (boundSigns.isEmpty()) return 0;
        int maxSlot = -1;
        for (int slot : boundSigns.values()) {
            if (slot > maxSlot) maxSlot = slot;
        }
        return maxSlot + 1;
    }

    public void bindSign(Location loc, int slot) {
        boundSigns.put(loc, slot);
        save();
        updateSignAt(loc);
    }

    public boolean unbindSign(Location loc) {
        if (boundSigns.remove(loc) != null) {
            save();
            Block block = loc.getBlock();
            if (block.getState() instanceof Sign sign) {
                SignSide side = sign.getSide(Side.FRONT);
                side.line(0, Component.text("§c[Отвязано]"));
                side.line(1, Component.empty());
                side.line(2, Component.empty());
                side.line(3, Component.empty());
                sign.update(true, false);
            }
            return true;
        }
        return false;
    }

    public Integer getSlot(Location loc) {
        return boundSigns.get(loc);
    }

    public Map<Location, Integer> getBoundSigns() {
        return Collections.unmodifiableMap(boundSigns);
    }

    public void updateAllSigns() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Location loc : boundSigns.keySet()) {
                updateSignAt(loc);
            }
        });
    }

    public void updateSignAt(Location loc) {
        Integer slot = boundSigns.get(loc);
        if (slot == null || loc.getWorld() == null) return;

        Block block = loc.getBlock();
        if (!(block.getState() instanceof Sign sign)) return;

        List<Contract> active = plugin.getContractManager().getActiveContracts();
        Contract contract = (slot < active.size()) ? active.get(slot) : null;

        SignSide side = sign.getSide(Side.FRONT);
        side.line(0, mm.deserialize("<gradient:#55FF55:#55FFFF><b>[Контракт #" + (slot + 1) + "]</b></gradient>"));

        if (contract != null) {
            String rawTitle = LegacyComponentSerializer.legacySection().serialize(mm.deserialize(contract.getDisplayName()));
            if (rawTitle.startsWith("§")) {
                rawTitle = rawTitle.substring(2);
            }
            if (rawTitle.length() > 15) {
                rawTitle = rawTitle.substring(0, 15);
            }
            side.line(1, Component.text("§e" + rawTitle));
            side.line(2, Component.text("§7" + formatDiff(contract.getDifficulty())));
            side.line(3, Component.text("§aПКМ: Взять"));
        } else {
            side.line(1, Component.text("§7[Нет контракта]"));
            side.line(2, Component.text("§eПКМ: Создать"));
            side.line(3, Component.text("§bЛКМ: Все квесты"));
        }

        sign.update(true, false);
    }

    private String formatDiff(Difficulty diff) {
        return switch (diff) {
            case STARTER -> "Начальный";
            case EASY -> "Легкий";
            case MEDIUM -> "Средний";
            case HARD -> "Сложный";
        };
    }
}
