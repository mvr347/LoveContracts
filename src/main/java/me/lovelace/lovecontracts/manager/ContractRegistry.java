package me.lovelace.lovecontracts.manager;

import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.condition.CatchFishCondition;
import me.lovelace.lovecontracts.condition.ContractCondition;
import me.lovelace.lovecontracts.condition.CraftItemCondition;
import me.lovelace.lovecontracts.condition.KillEntityCondition;
import me.lovelace.lovecontracts.condition.MineMaterialCondition;
import me.lovelace.lovecontracts.model.Contract;
import me.lovelace.lovecontracts.model.ContractType;
import me.lovelace.lovecontracts.model.Difficulty;
import me.lovelace.lovecontracts.model.Penalty;
import me.lovelace.lovecontracts.model.Reward;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads contract definitions from contracts.yml (not the SQLite {@code contracts} table,
 * which nothing currently reads) and keeps one shared {@link Contract}/{@link ContractCondition}
 * instance per id — progress is tracked per-player inside the condition itself.
 */
public class ContractRegistry {

    private final LoveContracts plugin;
    private final Map<String, Contract> contracts = new LinkedHashMap<>();

    public ContractRegistry(LoveContracts plugin) {
        this.plugin = plugin;
    }

    public void loadFromConfig() {
        shutdown();

        File file = new File(plugin.getDataFolder(), "contracts.yml");
        if (!file.exists()) {
            plugin.saveResource("contracts.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("contracts");
        if (root == null) {
            plugin.getLogger().warning("contracts.yml has no 'contracts' section");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            try {
                contracts.put(id, parseContract(id, section));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load contract '" + id + "': " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + contracts.size() + " contract definitions");
    }

    private Contract parseContract(String id, ConfigurationSection section) {
        String displayName = section.getString("display-name", id);
        String description = section.getString("description", "");
        Difficulty difficulty = Difficulty.fromString(section.getString("difficulty"));
        ContractType type = ContractType.fromString(section.getString("type"));
        int maxAcceptances = section.getInt("max-acceptances", -1);
        int dailySpawns = section.getInt("daily-spawns", 1);
        int weight = section.getInt("weight", 0);
        int expirationHours = section.getInt("expiration-hours", 24);
        boolean enabled = section.getBoolean("enabled", true);

        List<Reward> rewards = parseRewards(section.getConfigurationSection("rewards"));
        List<Penalty> penalties = parsePenalties(section.getConfigurationSection("penalties"));

        Contract contract = new Contract(id, displayName, description, difficulty, type,
                maxAcceptances, dailySpawns, weight, expirationHours, enabled, rewards, penalties);

        contract.setCondition(parseCondition(contract, section.getConfigurationSection("condition")));
        return contract;
    }

    private List<Reward> parseRewards(ConfigurationSection section) {
        List<Reward> rewards = new ArrayList<>();
        if (section == null) return rewards;

        if (section.contains("money")) {
            rewards.add(Reward.money(section.getDouble("money")));
        }
        if (section.contains("experience")) {
            rewards.add(new Reward(Reward.Type.EXPERIENCE, section.getDouble("experience")));
        }
        ConfigurationSection rep = section.getConfigurationSection("reputation");
        if (rep != null) {
            for (String repType : rep.getKeys(false)) {
                rewards.add(Reward.reputation(repType, rep.getDouble(repType)));
            }
        }
        for (String itemLine : section.getStringList("items")) {
            ItemStack stack = parseItemLine(itemLine);
            if (stack != null) rewards.add(Reward.item(stack));
        }
        return rewards;
    }

    private List<Penalty> parsePenalties(ConfigurationSection section) {
        List<Penalty> penalties = new ArrayList<>();
        if (section == null || section.getBoolean("none", false)) {
            return penalties;
        }
        if (section.contains("money")) {
            penalties.add(Penalty.money(section.getDouble("money")));
        }
        ConfigurationSection rep = section.getConfigurationSection("reputation");
        if (rep != null) {
            for (String repType : rep.getKeys(false)) {
                penalties.add(Penalty.reputation(repType, rep.getDouble(repType)));
            }
        }
        return penalties;
    }

    private ItemStack parseItemLine(String line) {
        if (line == null || line.isBlank()) return null;
        String[] parts = line.trim().split("\\s+");
        try {
            Material material = Material.matchMaterial(parts[0]);
            if (material == null) return null;
            int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            return new ItemStack(material, Math.max(1, amount));
        } catch (Exception e) {
            return null;
        }
    }

    private ContractCondition parseCondition(Contract contract, ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("missing condition section");
        }
        String type = section.getString("type", "");
        int count = section.getInt("count", 1);

        return switch (type) {
            case "KillEntity" -> {
                EntityType entityType = EntityType.valueOf(section.getString("entity-type", "ZOMBIE").toUpperCase());
                yield new KillEntityCondition(contract, entityType, count);
            }
            case "MineMaterial" -> {
                Material material = Material.matchMaterial(section.getString("material", "STONE"));
                yield new MineMaterialCondition(contract, material != null ? material : Material.STONE, count);
            }
            case "CatchFish" -> new CatchFishCondition(contract, count);
            case "CraftItem" -> {
                Material material = Material.matchMaterial(section.getString("material", "STICK"));
                yield new CraftItemCondition(contract, material != null ? material : Material.STICK, count);
            }
            default -> throw new IllegalArgumentException("unknown condition type: " + type);
        };
    }

    public Contract getContract(String id) {
        return contracts.get(id);
    }

    public List<Contract> getEnabled() {
        return contracts.values().stream().filter(Contract::isEnabled).toList();
    }

    public Collection<Contract> getAll() {
        return Collections.unmodifiableCollection(contracts.values());
    }

    public int size() {
        return contracts.size();
    }

    public void shutdown() {
        contracts.values().forEach(c -> {
            if (c.getCondition() != null) {
                c.getCondition().shutdown();
            }
        });
        contracts.clear();
    }
}
