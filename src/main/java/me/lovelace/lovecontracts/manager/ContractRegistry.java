package me.lovelace.lovecontracts.manager;

import me.lovelace.lovecontracts.util.EconomyConfig;
import me.lovelace.lovecontracts.util.ModelPricing;
import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.condition.CatchFishCondition;
import me.lovelace.lovecontracts.condition.ContractCondition;
import me.lovelace.lovecontracts.condition.CraftItemCondition;
import me.lovelace.lovecontracts.condition.KillEntityCondition;
import me.lovelace.lovecontracts.condition.MineMaterialCondition;
import me.lovelace.lovecontracts.model.Contract;
import me.lovelace.lovecontracts.model.ContractRequirement;
import me.lovelace.lovecontracts.model.ContractType;
import me.lovelace.lovecontracts.model.Difficulty;
import me.lovelace.lovecontracts.model.Penalty;
import me.lovelace.lovecontracts.model.Reward;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

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

    public void validateDefinitions(java.util.Map<String,Object> definitions) {
        var yaml = new YamlConfiguration();
        var root = yaml.createSection("contracts", definitions);
        for (String id : root.getKeys(false)) parseContract(id, root.getConfigurationSection(id));
        var old = new YamlConfiguration();
        try {
            old.load(new File(plugin.getDataFolder(), "contracts.yml"));
            var oldGoals = new java.util.HashMap<String,Object>(); var newGoals = new java.util.HashMap<String,Object>();
            var original = old.getConfigurationSection("contracts");
            if (original != null) for (String id : original.getKeys(false)) oldGoals.put(id, conditionValues(original.getConfigurationSection(id+".condition")));
            for (String id : root.getKeys(false)) newGoals.put(id, conditionValues(root.getConfigurationSection(id+".condition")));
            var active = new java.util.HashSet<String>();
            try (var c=plugin.getDatabase().getConnection(); var q=c.createStatement(); var rows=q.executeQuery("SELECT contract_id FROM player_contracts WHERE completed_at IS NULL AND failed_at IS NULL UNION SELECT substr(quest_id,7) FROM player_npc_quests WHERE status='ACTIVE' AND substr(quest_id,1,6)='story_'")) {
                while(rows.next())active.add(rows.getString(1));
            }
            me.lovelace.lovecontracts.service.ActiveDefinitionGuard.validate(oldGoals,newGoals,active);
        } catch (IllegalArgumentException error) { throw error; }
        catch (Exception error) { throw new IllegalStateException("Cannot inspect active contracts", error); }
    }

    private java.util.Map<String,Object> conditionValues(ConfigurationSection section) {
        var result=new java.util.LinkedHashMap<String,Object>();
        if(section!=null)section.getValues(true).forEach((k,v)->{if(!(v instanceof ConfigurationSection))result.put(k,v);});
        return result;
    }

    private Contract parseContract(String id, ConfigurationSection section) {
        String displayName = section.getString("display-name", id);
        String description = section.getString("description", "");
        Difficulty difficulty = Difficulty.fromString(section.getString("difficulty"));
        ContractType type = ContractType.fromString(section.getString("type"));
        int maxAcceptances = section.getInt("max-acceptances", -1);
        int dailySpawns = section.getInt("daily-spawns", 1);
        int weight = section.getInt("weight", 0);
        int expirationMinutes = section.contains("expiration-minutes")
                ? section.getInt("expiration-minutes")
                : section.getInt("expiration-hours", 24) * 60;
        boolean starter = section.getBoolean("starter", section.getBoolean("is-starter", false));
        boolean enabled = section.getBoolean("enabled", true);

        ConfigurationSection cond = section.getConfigurationSection("condition");
        ConfigurationSection rewardSec = section.getConfigurationSection("rewards");
        java.util.function.DoubleSupplier ratio = cond == null || rewardSec == null ? () -> 1.0
                : ModelPricing.ratio(plugin, cond.getString("material"), cond.getLong("count", 0),
                        EconomyConfig.read(plugin, rewardSec, "money", EconomyConfig.REWARD_SCALE));
        List<Reward> rewards = parseRewards(rewardSec, ratio);
        List<Penalty> penalties = parsePenalties(section.getConfigurationSection("penalties"), ratio);
        ContractRequirement requirement = parseRequirements(section.getConfigurationSection("requirements"));

        Contract contract = new Contract(id, displayName, description, difficulty, type,
                maxAcceptances, dailySpawns, weight, expirationMinutes, starter, enabled, rewards, penalties, requirement);

        var conditionSection = section.getConfigurationSection("condition");
        if (conditionSection != null && conditionSection.isConfigurationSection("location"))
            contract.setTargetLocation(me.lovelace.lovecontracts.npc.model.QuestLocation.parse(conditionSection.getConfigurationSection("location").getValues(false)));
        contract.setCompletionAchievement(section.getString("completion-achievement"));
        contract.setRequiredAchievement(section.getString("requires-achievement"));
        contract.setCondition(parseCondition(contract, section.getConfigurationSection("condition")));
        return contract;
    }

    private ContractRequirement parseRequirements(ConfigurationSection sec) {
        if (sec == null) return null;
        int minLevel = sec.getInt("min-level", 0);
        String perm = sec.getString("permission", null);
        int minCompleted = sec.getInt("min-completed-contracts", 0);
        if (minLevel <= 0 && perm == null && minCompleted <= 0) return null;
        return new ContractRequirement(minLevel, perm, minCompleted);
    }

    /**
     * Contract rewards are physical LoveCore currency only — see {@link Reward}. Only
     * {@code money} is read from the {@code rewards} section; any {@code items}/{@code
     * experience}/{@code reputation} keys left over in an old {@code contracts.yml} are
     * ignored rather than silently miscounted.
     */
    private List<Reward> parseRewards(ConfigurationSection section, java.util.function.DoubleSupplier ratio) {
        List<Reward> rewards = new ArrayList<>();
        if (section == null) return rewards;

        if (section.contains("money")) {
            rewards.add(Reward.money(EconomyConfig.read(plugin, section, "money", EconomyConfig.REWARD_SCALE), ratio).capped(plugin.getConfig().getLong("economy.contracts-v2.max-reward", 240)));
        }
        return rewards;
    }

    private List<Penalty> parsePenalties(ConfigurationSection section, java.util.function.DoubleSupplier ratio) {
        List<Penalty> penalties = new ArrayList<>();
        if (section == null || section.getBoolean("none", false)) {
            return penalties;
        }
        if (section.contains("money")) {
            penalties.add(Penalty.money(EconomyConfig.read(plugin, section, "money", EconomyConfig.PENALTY_SCALE), ratio));
        }
        ConfigurationSection rep = section.getConfigurationSection("reputation");
        if (rep != null) {
            for (String repType : rep.getKeys(false)) {
                penalties.add(Penalty.reputation(repType, rep.getDouble(repType)));
            }
        }
        return penalties;
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
                if (material == null) throw new IllegalArgumentException("Unknown material");
                yield new MineMaterialCondition(contract, material, count);
            }
            case "CatchFish" -> new CatchFishCondition(contract, count);
            case "CraftItem" -> {
                Material material = Material.matchMaterial(section.getString("material", "STICK"));
                if (material == null) throw new IllegalArgumentException("Unknown material");
                yield new CraftItemCondition(contract, material, count);
            }
            default -> throw new IllegalArgumentException("unknown condition type: " + type);
        };
    }

    public Contract getContract(String id) {
        return contracts.get(id);
    }

    public void registerContract(Contract contract) {
        if (contract != null) {
            contracts.put(contract.getId(), contract);
        }
    }

    public List<Contract> getEnabled() {
        return contracts.values().stream().filter(Contract::isEnabled)
                .filter(c -> plugin.getNpcQuestManager() == null || !plugin.getNpcQuestManager().isStoryTemplate(c.getId())).toList();
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
