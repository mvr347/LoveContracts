package me.lovelace.lovecontracts.npc.manager;

import me.lovelace.lovecontracts.gui.board.DurationText;
import me.lovelace.lovecontracts.model.Difficulty;
import me.lovelace.lovecontracts.model.Reward;
import me.lovelace.lovecontracts.npc.storage.NpcQuestStorage;
import me.lovelace.lovecontracts.util.CoinFormat;
import me.lovelace.lovecontracts.util.Notify;
import me.lovelace.lovecontracts.LoveContracts;
import me.lovelace.lovecontracts.integration.NpcSettings;
import me.lovelace.lovecontracts.npc.model.*;
import me.lovelace.lovecontracts.npc.storage.NpcQuestDatabase;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class NpcQuestManager implements Listener {

    public static class ActiveQuestProgress {
        private int currentStep;
        private int stepProgress;
        private long startedAt;
        private boolean noDeaths;
        private int bonusInteractions;
        private int surplusDelivered;

        public ActiveQuestProgress(int currentStep, int stepProgress) {
            this(currentStep, stepProgress, System.currentTimeMillis(), true, 0, 0);
        }

        public ActiveQuestProgress(int currentStep, int stepProgress, long startedAt,
                                   boolean noDeaths, int bonusInteractions, int surplusDelivered) {
            this.currentStep = currentStep;
            this.stepProgress = stepProgress;
            this.startedAt = startedAt;
            this.noDeaths = noDeaths;
            this.bonusInteractions = bonusInteractions;
            this.surplusDelivered = surplusDelivered;
        }

        public int getCurrentStep() { return currentStep; }
        public int getStepProgress() { return stepProgress; }
        public long getStartedAt() { return startedAt; }
        public boolean isNoDeaths() { return noDeaths; }
        public int getBonusInteractions() { return bonusInteractions; }
        public int getSurplusDelivered() { return surplusDelivered; }

        public void setCurrentStep(int currentStep) { this.currentStep = currentStep; }
        public void setStepProgress(int stepProgress) { this.stepProgress = stepProgress; }
        public void setStartedAt(long startedAt) { this.startedAt = startedAt; }
        public void setNoDeaths(boolean noDeaths) { this.noDeaths = noDeaths; }
        public void addBonusInteraction() { this.bonusInteractions++; }
        public void addSurplusDelivered(int extra) { if (extra > 0) this.surplusDelivered += extra; }
    }

    private final LoveContracts plugin;
    private final Map<UUID, Map<String, Long>> completed = new HashMap<>();
    private final Map<UUID, UUID> sessions = new HashMap<>();
    private final Set<String> pending = new HashSet<>();
    private final Map<String,String> completionAchievements = new HashMap<>(), requiredAchievements = new HashMap<>();
    private final Set<String> convertedTemplates = new HashSet<>();
    private final Map<String, me.lovelace.lovecontracts.model.ContractRequirement> requirements = new HashMap<>();
    private final java.util.concurrent.ExecutorService writer = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<String, NpcQuest> questMap = new LinkedHashMap<>();
    /** Chains that came from chains.yml, the file of the in-game builder (the only ones the builder may rewrite). */
    private final Set<String> builderIds = new HashSet<>();
    private final me.lovelace.lovecontracts.npc.builder.ChainStore chainStore;
    private final Map<UUID, Map<String, ActiveQuestProgress>> activePlayerQuests = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> questboardEnabled = new ConcurrentHashMap<>();

    public NpcQuestManager(LoveContracts plugin, NpcQuestDatabase db) {
        this.plugin = plugin;
        this.chainStore = new me.lovelace.lovecontracts.npc.builder.ChainStore(plugin.getDataFolder());
    }

    public me.lovelace.lovecontracts.npc.builder.ChainStore chainStore() { return chainStore; }

    /** Whether the chain lives in chains.yml, so the builder may edit and delete it. */
    public boolean isBuilderChain(String id) { return builderIds.contains(id); }

    /** Every loaded chain, in loading order: the hand-written files first, then the builder's. */
    public Collection<NpcQuest> allQuests() { return java.util.Collections.unmodifiableCollection(questMap.values()); }

    public boolean isQuestboardEnabled(UUID uuid) {
        return uuid == null || questboardEnabled.getOrDefault(uuid, true);
    }

    public void setQuestboardEnabled(UUID uuid, boolean enabled) {
        if (uuid == null) return;
        questboardEnabled.put(uuid, enabled);
        writer.execute(() -> {
            try (Connection c = plugin.getDatabase().getConnection()) {
                NpcQuestStorage.saveQuestboardEnabled(c, uuid, enabled);
            } catch (SQLException error) { failure(error); }
        });
    }

    public void cleanupPlayer(UUID uuid) {
        if (uuid != null) {
            activePlayerQuests.remove(uuid);
            completed.remove(uuid);
            sessions.remove(uuid);
            questboardEnabled.remove(uuid);
        }
    }

    public void loadFromConfig() {
        questMap.clear();
        completionAchievements.clear(); requiredAchievements.clear();
        convertedTemplates.clear();
        requirements.clear();
        builderIds.clear();

        File file = new File(plugin.getDataFolder(), "npc_quests.yml");
        if (!file.exists()) {
            plugin.saveResource("npc_quests.yml", false);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("npc_quests");
        if (root == null) {
            plugin.getLogger().warning("npc_quests.yml has no 'npc_quests' section; loading additional stories only");
            root = yaml.createSection("npc_quests");
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null || !sec.getBoolean("enabled", true)) continue;
            try {
                NpcQuest quest = parseQuest(id, sec);
                readAchievementLinks(id, sec);
                questMap.put(id, quest);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load NPC quest '" + id + "'", e);
            }
        }

        File extra = new File(plugin.getDataFolder(), "npc_stories.yml");
        if (!extra.exists()) plugin.saveResource("npc_stories.yml", false);
        ConfigurationSection stories = YamlConfiguration.loadConfiguration(extra).getConfigurationSection("npc_quests");
        if (stories != null) for (String id : stories.getKeys(false)) {
            if (questMap.containsKey(id) || !stories.getBoolean(id + ".enabled", true)) continue;
            try {
                NpcQuest quest = parseQuest(id, stories.getConfigurationSection(id));
                readAchievementLinks(id, stories.getConfigurationSection(id));
                questMap.put(id, quest);
            } catch (Exception error) { plugin.getLogger().log(Level.WARNING, "Invalid NPC story: " + id, error); }
        }
        // chains.yml belongs to the in-game builder; a hand-written chain with the same id wins
        if (chainStore.file().exists()) {
            ConfigurationSection built = YamlConfiguration.loadConfiguration(chainStore.file()).getConfigurationSection("npc_quests");
            if (built != null) for (String id : built.getKeys(false)) {
                if (questMap.containsKey(id)) {
                    plugin.getLogger().warning("chains.yml: chain '" + id + "' is shadowed by a chain with the same id in npc_quests.yml or npc_stories.yml");
                    continue;
                }
                if (!built.getBoolean(id + ".enabled", true)) continue;
                try {
                    NpcQuest quest = parseQuest(id, built.getConfigurationSection(id));
                    readAchievementLinks(id, built.getConfigurationSection(id));
                    questMap.put(id, quest);
                    builderIds.add(id);
                } catch (Exception error) { plugin.getLogger().log(Level.WARNING, "Invalid chain in chains.yml: " + id, error); }
            }
        }
        loadComplexStories();
        plugin.getLogger().info("Loaded " + questMap.size() + " NPC chain quest definitions");
    }

    public boolean isStoryTemplate(String id) { return convertedTemplates.contains(id); }

    /** Keep simple board work; turn successfully parsed complex templates into ordered NPC stories. */
    private void loadComplexStories() {
        if (!plugin.getConfig().getBoolean("stories.convert-complex-orders", false)) return;
        ConfigurationSection root = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "contracts.yml")).getConfigurationSection("contracts");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true) || section.getBoolean("starter", false)) continue;
            String difficulty = section.getString("difficulty", "EASY");
            boolean unique = section.getString("type", "REPEATING").equalsIgnoreCase("ONE_TIME");
            if (!unique && !difficulty.equalsIgnoreCase("MEDIUM") && !difficulty.equalsIgnoreCase("HARD")) continue;
            String storyId = "story_" + id;
            if (questMap.containsKey(storyId)) { convertedTemplates.add(id); continue; }
            try {
                ConfigurationSection condition = section.getConfigurationSection("condition");
                if (condition == null) continue;
                NpcQuestStep.Type type = switch (condition.getString("type", "")) {
                    case "MineMaterial" -> NpcQuestStep.Type.MINE_MATERIAL;
                    case "CraftItem" -> NpcQuestStep.Type.CRAFT_ITEM;
                    case "KillEntity" -> NpcQuestStep.Type.KILL_ENTITY;
                    case "CatchFish" -> NpcQuestStep.Type.CATCH_FISH;
                    default -> throw new IllegalArgumentException("Unsupported condition");
                };
                String giver = section.getString("story-npc-key", "@contractor");
                String name = section.getString("display-name", id);
                NpcDialogueNode greeting = new NpcDialogueNode("greeting", List.of(section.getString("description", name),
                        "Сначала обсудим подробности, затем выполни работу и вернись с отчётом."), List.of(
                        new NpcDialogueOption("Беру поручение", NpcDialogueOption.Action.ACCEPT_QUEST, null),
                        new NpcDialogueOption("Позже", NpcDialogueOption.Action.CLOSE, null)));
                NpcDialogueNode brief = new NpcDialogueNode("visit", List.of("Заказ: " + name,
                        "Работа засчитывается только после этого разговора. После выполнения вернись за оплатой."),
                        List.of(new NpcDialogueOption("Условия понятны", NpcDialogueOption.Action.COMPLETE_STEP, null)));
                Material material = condition.contains("material") ? Material.valueOf(condition.getString("material")) : null;
                EntityType entity = condition.contains("entity-type") ? EntityType.valueOf(condition.getString("entity-type")) : null;
                if ((type == NpcQuestStep.Type.MINE_MATERIAL || type == NpcQuestStep.Type.CRAFT_ITEM) && material == null
                        || type == NpcQuestStep.Type.KILL_ENTITY && entity == null) throw new IllegalArgumentException("Missing target");
                int count = condition.getInt("count", 1);
                List<NpcQuestStep> steps = List.of(
                        new NpcQuestStep(1, NpcQuestStep.Type.VISIT_NPC, null, null, giver, "Заказчик", 1, "Обсудите условия с заказчиком", brief),
                        new NpcQuestStep(2, type, material, entity, null, "", count,
                            name + " — " + (material != null ? material : entity != null ? entity : "улов") + " × " + count, null,
                            condition.isConfigurationSection("location") ? QuestLocation.parse(condition.getConfigurationSection("location").getValues(false)) : null, Map.of()),
                        new NpcQuestStep(3, NpcQuestStep.Type.VISIT_NPC, null, null, giver, "Заказчик", 1, "Вернитесь к заказчику с отчётом", null));
                questMap.putIfAbsent(storyId, new NpcQuest(storyId, giver, name, !unique, 1440,
                        "greeting", Map.of("greeting", greeting), steps, parseRewards(section.getConfigurationSection("rewards")),
                        parseTier(difficulty), section.getInt("expiration-minutes", 0), section.getString("description", ""), RewardTiers.DEFAULT));
                ConfigurationSection req = section.getConfigurationSection("requirements");
                if (req != null) requirements.put(storyId, new me.lovelace.lovecontracts.model.ContractRequirement(
                        req.getInt("min-level", 0), req.getString("permission"), req.getInt("min-completed-contracts", 0)));
                readAchievementLinks(storyId, section);
                convertedTemplates.add(id);
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Keeping complex order on ordinary board: " + id + ": " + error.getMessage());
            }
        }
    }

    private void readAchievementLinks(String id, ConfigurationSection section) {
        String completed=section.getString("completion-achievement"), required=section.getString("requires-achievement");
        if(completed!=null&&!completed.isBlank())completionAchievements.put(id,completed);
        if(required!=null&&!required.isBlank())requiredAchievements.put(id,required);
    }

    /**
     * Checks chains the builder is about to write to chains.yml: every one must parse like a hand-written chain, and a chain
     * somebody is on right now must keep its steps (the same guard as for npc_stories.yml). Throws
     * {@link IllegalArgumentException} with a sentence for the admin.
     */
    public void validateChainDefinitions(java.util.Map<String, Object> definitions) {
        var yaml = new YamlConfiguration();
        var root = yaml.createSection("npc_quests", definitions);
        for (String id : root.getKeys(false)) parseQuest(id, root.getConfigurationSection(id));
        var oldGoals = new HashMap<String, Object>();
        var newGoals = new HashMap<String, Object>();
        if (chainStore.file().exists()) {
            var original = YamlConfiguration.loadConfiguration(chainStore.file()).getConfigurationSection("npc_quests");
            if (original != null) for (String id : original.getKeys(false)) oldGoals.put(id, original.getList(id + ".steps", List.of()));
        }
        for (String id : root.getKeys(false)) newGoals.put(id, root.getList(id + ".steps", List.of()));
        Set<String> active = new HashSet<>();
        try (var c = plugin.getDatabase().getConnection(); var q = c.createStatement();
             var rows = q.executeQuery("SELECT DISTINCT quest_id FROM player_npc_quests WHERE status='ACTIVE'")) {
            while (rows.next()) active.add(rows.getString(1));
        } catch (SQLException error) {
            throw new IllegalStateException("Cannot inspect active chains", error);
        }
        me.lovelace.lovecontracts.service.ActiveDefinitionGuard.validate(oldGoals, newGoals, active);
    }

    public void validateDefinitions(java.util.Map<String,Object> definitions) {
        var yaml = new YamlConfiguration();
        var root = yaml.createSection("npc_quests", definitions);
        for (String id : root.getKeys(false)) parseQuest(id, root.getConfigurationSection(id));
        var old = new YamlConfiguration();
        try {
            old.load(new File(plugin.getDataFolder(), "npc_stories.yml"));
            var oldGoals=new HashMap<String,Object>();var newGoals=new HashMap<String,Object>();
            var original=old.getConfigurationSection("npc_quests");
            if(original!=null)for(String id:original.getKeys(false))oldGoals.put(id,original.getList(id+".steps",List.of()));
            for(String id:root.getKeys(false))newGoals.put(id,root.getList(id+".steps",List.of()));
            Set<String> active=new HashSet<>();
            try(var c=plugin.getDatabase().getConnection();var q=c.createStatement();var rows=q.executeQuery("SELECT DISTINCT quest_id FROM player_npc_quests WHERE status='ACTIVE'")){
                while(rows.next())active.add(rows.getString(1));
            }
            me.lovelace.lovecontracts.service.ActiveDefinitionGuard.validate(oldGoals,newGoals,active);
        } catch(IllegalArgumentException error){throw error;}
        catch(Exception error){throw new IllegalStateException("Cannot inspect active stories",error);}
    }

    private NpcQuest parseQuest(String id, ConfigurationSection sec) {
        // npc-key is the provider key; npc-id is the pre-provider spelling (a numeric id) and stays readable.
        String npcKey = NpcSettings.readKey(sec.get("npc-key"), sec.get("npc-id"));
        String name = sec.getString("display-name", id);
        boolean repeatable = sec.getBoolean("repeatable", true);
        int cooldown = sec.getInt("cooldown-minutes", 0);
        String initialDialogue = sec.getString("initial-dialogue", "greeting");
        Difficulty tier = parseTier(sec.getString("tier", "EASY"));
        int timeLimit = Math.max(0, sec.getInt("time-limit-minutes", 0));
        String summary = sec.getString("summary", "");
        RewardTiers rewardTiers = RewardTiers.parse(sec.getConfigurationSection("reward-tiers"));

        Map<String, NpcDialogueNode> dialogues = new HashMap<>();
        ConfigurationSection diagSec = sec.getConfigurationSection("dialogues");
        if (diagSec != null) {
            for (String nodeKey : diagSec.getKeys(false)) {
                ConfigurationSection nodeSec = diagSec.getConfigurationSection(nodeKey);
                if (nodeSec != null) {
                    dialogues.put(nodeKey, parseDialogueNode(nodeKey, nodeSec));
                }
            }
        }

        List<NpcQuestStep> steps = new ArrayList<>();
        List<?> stepList = sec.getList("steps");
        if (stepList != null) {
            for (int i = 0; i < stepList.size(); i++) {
                if (stepList.get(i) instanceof Map<?, ?> map) {
                    steps.add(parseStep(i + 1, map));
                }
            }
        }

        if (steps.isEmpty()) throw new IllegalArgumentException("Quest must have at least one step");
        for (NpcQuestStep step : steps) {
            if ((step.getType() == NpcQuestStep.Type.VISIT_NPC || step.getType() == NpcQuestStep.Type.DELIVER_ITEM)
                    && step.getTargetNpcKey() == null) throw new IllegalArgumentException("Missing target NPC");
            if ((step.getType() == NpcQuestStep.Type.CRAFT_ITEM || step.getType() == NpcQuestStep.Type.MINE_MATERIAL
                    || step.getType() == NpcQuestStep.Type.DELIVER_ITEM) && step.getMaterial() == null)
                throw new IllegalArgumentException("Missing material");
            if (step.getType() == NpcQuestStep.Type.KILL_ENTITY && step.getEntityType() == null)
                throw new IllegalArgumentException("Missing entity type");
        }
        if (!dialogues.containsKey(initialDialogue)) throw new IllegalArgumentException("Missing initial dialogue");
        return new NpcQuest(id, npcKey, name, repeatable, cooldown, initialDialogue, dialogues, steps,
                parseRewards(sec.getConfigurationSection("rewards")), tier, timeLimit, summary, rewardTiers);
    }

    private static Difficulty parseTier(String raw) {
        if (raw == null || raw.isBlank()) return Difficulty.EASY;
        try {
            Difficulty d = Difficulty.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            return d == Difficulty.STARTER ? Difficulty.EASY : d;
        } catch (IllegalArgumentException ignored) {
            return Difficulty.EASY;
        }
    }

    private NpcDialogueNode parseDialogueNode(String nodeId, ConfigurationSection sec) {
        List<String> lines = sec.getStringList("text");
        List<NpcDialogueOption> options = new ArrayList<>();
        for (Map<?, ?> optMap : sec.getMapList("options")) {
            String text = getMapString(optMap, "text", "");
            String actionStr = getMapString(optMap, "action", optMap.containsKey("next-node") ? "NEXT_NODE" : "CLOSE");
            String nextNode = optMap.containsKey("next-node") ? getMapString(optMap, "next-node", null) : null;
            boolean bonus = Boolean.parseBoolean(getMapString(optMap, "bonus", "false"));
            NpcDialogueOption.Action action;
            try {
                action = NpcDialogueOption.Action.valueOf(actionStr);
            } catch (Exception e) {
                action = NpcDialogueOption.Action.CLOSE;
            }
            options.add(new NpcDialogueOption(text, action, nextNode, bonus));
        }
        return new NpcDialogueNode(nodeId, lines, options);
    }

    private String getMapString(Map<?, ?> map, String key, String defaultValue) {
        if (map == null) return defaultValue;
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private NpcQuestStep parseStep(int number, Map<?, ?> map) {
        String typeStr = getMapString(map, "type", "CRAFT_ITEM");
        NpcQuestStep.Type type;
        try {
            type = NpcQuestStep.Type.valueOf(typeStr);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown quest step type: " + typeStr);
        }
        String matStr = getMapString(map, "material", null);
        Material mat = null;
        if (matStr != null) {
            try { mat = Material.valueOf(matStr); } catch (Exception ignored) {}
        }
        String entStr = getMapString(map, "entity-type", null);
        EntityType ent = null;
        if (entStr != null) {
            try { ent = EntityType.valueOf(entStr); } catch (Exception ignored) {}
        }
        String targetNpcKey = NpcSettings.readKey(map.get("target-npc-key"), map.get("target-npc-id"));
        String targetNpcName = getMapString(map, "target-npc-name", "");
        int count = Integer.parseInt(getMapString(map, "count", "1"));
        String desc = getMapString(map, "description", "");

        NpcDialogueNode visitDialogue = map.get("dialogue") instanceof Map<?, ?> dialogue ? parseStepDialogue(dialogue) : null;
        Map<String, NpcDialogueNode> reactions = new HashMap<>();
        if (map.get("dialogue") instanceof Map<?, ?> dialogue && dialogue.get("reactions") instanceof Map<?, ?> variants)
            variants.forEach((mood, value) -> { if (value instanceof Map<?, ?> node) reactions.put(mood.toString(), parseStepDialogue(node)); });
        QuestLocation location = map.get("location") instanceof Map<?, ?> at ? QuestLocation.parse(at) : null;
        if (type == NpcQuestStep.Type.VISIT_LOCATION && count != 1) throw new IllegalArgumentException("VISIT_LOCATION count must be 1");
        if (type == NpcQuestStep.Type.VISIT_LOCATION && location == null) throw new IllegalArgumentException("VISIT_LOCATION needs location");
        return new NpcQuestStep(number, type, mat, ent, targetNpcKey, targetNpcName, count, desc, visitDialogue, location, reactions);
    }

    private NpcDialogueNode parseStepDialogue(Map<?, ?> dMap) {
        List<String> lines = new ArrayList<>();
        if (dMap.get("text") instanceof List<?> tList) {
            for (Object item : tList) {
                if (item != null) lines.add(item.toString());
            }
        }
        List<NpcDialogueOption> options = new ArrayList<>();
        if (dMap.get("options") instanceof List<?> oList) {
            for (Object o : oList) {
                if (o instanceof Map<?, ?> om) {
                    String text = getMapString(om, "text", "");
                    String act = getMapString(om, "action", om.containsKey("next-node") ? "NEXT_NODE" : "COMPLETE_STEP");
                    boolean bonus = Boolean.parseBoolean(getMapString(om, "bonus", "false"));
                    NpcDialogueOption.Action action;
                    try { action = NpcDialogueOption.Action.valueOf(act); } catch (Exception e) { action = NpcDialogueOption.Action.COMPLETE_STEP; }
                    options.add(new NpcDialogueOption(text, action, getMapString(om, "next-node", null), bonus));
                }
            }
        }
        return new NpcDialogueNode("visit", lines, options);
    }

    /** NPC quest rewards are physical LoveCore currency only — see {@link me.lovelace.lovecontracts.model.Reward}. */
    private List<me.lovelace.lovecontracts.model.Reward> parseRewards(ConfigurationSection sec) {
        List<me.lovelace.lovecontracts.model.Reward> rewards = new ArrayList<>();
        if (sec == null) return rewards;
        if (sec.contains("money")) {
            rewards.add(me.lovelace.lovecontracts.model.Reward.money(
                    me.lovelace.lovecontracts.util.EconomyConfig.read(plugin, sec, "money",
                            me.lovelace.lovecontracts.util.EconomyConfig.REWARD_SCALE)).capped(plugin.getConfig().getLong("economy.contracts-v2.max-reward", 240)));
        }
        return rewards;
    }

    public List<NpcQuest> getQuestsForNpc(String npcKey) {
        String key = NpcSettings.normalizeKey(npcKey);
        return key == null ? List.of() : questMap.values().stream().filter(q -> matches(q.getNpcKey(), key)).toList();
    }

    private String resolveKey(String key) {
        if (key != null && key.startsWith("@actor:")) {
            var actor = plugin.getConfig().getConfigurationSection("npc-actors." + key.substring(7));
            if (actor == null) return null;
            String bound = actor.getString("npc-key");
            if (bound != null) return bound.startsWith("@") ? null : NpcSettings.normalizeKey(bound);
            String tag = actor.getString("tag-key"), value = actor.getString("tag-value");
            if (tag == null || value == null) return null;
            return plugin.getNpcBridge().provider().flatMap(p -> p.all().stream()
                    .filter(n -> n.tag(tag).filter(value::equals).isPresent()).findFirst())
                    .map(dev.lovelace.lovecore.api.npc.NpcHandle::key).orElse(null);
        }
        if ("@contractor".equals(key)) return plugin.getNpcBridge().boundKey();
        if (key != null && key.startsWith("@brew:")) return plugin.getNpcBridge().provider()
                .flatMap(p -> p.all().stream().filter(n -> n.tag("lovebrew.trader").filter(key.substring(6)::equals).isPresent()).findFirst())
                .map(dev.lovelace.lovecore.api.npc.NpcHandle::key).orElse(null);
        return key;
    }
    private boolean matches(String configured, String actual) {
        return actual != null && actual.equals(resolveKey(configured));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { loadPlayerActiveQuestsFromDb(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { cleanupPlayer(event.getPlayer().getUniqueId()); }

    public void loadPlayerActiveQuestsFromDb(UUID uuid) {
        UUID session = UUID.randomUUID();
        sessions.put(uuid, session);
        completed.remove(uuid);
        activePlayerQuests.remove(uuid);
        writer.execute(() -> {
            Map<String, ActiveQuestProgress> active = new LinkedHashMap<>();
            Map<String, Long> history = new HashMap<>();
            boolean qbEnabled = true;
            try (Connection c = plugin.getDatabase().getConnection()) {
                qbEnabled = NpcQuestStorage.loadQuestboardEnabled(c, uuid);
                try (var q = c.prepareStatement(
                        "SELECT quest_id,current_step,step_progress,status,COALESCE(strftime('%s',last_completed_at),0),"
                                + "started_at,no_deaths,bonus_interactions,surplus_delivered FROM player_npc_quests WHERE player_uuid=?")) {
                    q.setString(1, uuid.toString());
                    try (var rs = q.executeQuery()) {
                        while (rs.next()) {
                            String status = rs.getString(4);
                            if ("ACTIVE".equals(status)) {
                                active.put(rs.getString(1), new ActiveQuestProgress(
                                        rs.getInt(2), rs.getInt(3), rs.getLong(6),
                                        rs.getInt(7) != 0, rs.getInt(8), rs.getInt(9)));
                            } else if (!"ABANDONED".equals(status) && !"EXPIRED".equals(status)) {
                                history.put(rs.getString(1), rs.getLong(5) * 1000);
                            }
                        }
                    }
                }
                boolean loadedQb = qbEnabled;
                main(() -> {
                    if (!session.equals(sessions.get(uuid))) return;
                    questboardEnabled.put(uuid, loadedQb);
                    activePlayerQuests.put(uuid, active);
                    completed.put(uuid, history);
                    // an open board showed these chains as LOADING; redraw it now that the state is known
                    var board = plugin.getContractBoard();
                    Player online = Bukkit.getPlayer(uuid);
                    if (board != null && online != null) board.refresh(online);
                });
            } catch (SQLException error) { failure(error); }
        });
    }

    private void main(Runnable action) { Bukkit.getScheduler().runTask(plugin, action); }
    private void failure(Exception error) { plugin.getLogger().log(Level.WARNING, "NPC quest storage failed", error); }
    private String lock(UUID player, String quest) { return player + ":" + quest; }
    private ActiveQuestProgress state(Player player, NpcQuest quest) {
        return activePlayerQuests.getOrDefault(player.getUniqueId(), Map.of()).get(quest.getId());
    }

    public boolean canInteract(Player player, dev.lovelace.lovecore.api.npc.NpcHandle npc) {
        if (!player.isOnline() || npc == null || !npc.providerId().equals(plugin.getNpcBridge().providerId())) return false;
        return npc.entity().filter(e -> e.isValid() && e.getWorld().equals(player.getWorld()) && player.canSee(e)
                && e.getLocation().distanceSquared(player.getLocation()) <= 36).isPresent();
    }

    private boolean available(Player player, NpcQuest quest) {
        if (questMap.get(quest.getId()) != quest || plugin.getContractManager().isContractsDisabled(player)) return false;
        if (!me.lovelace.lovecontracts.service.ContractAchievementRewards.allowed(player.getUniqueId(), requiredAchievements.get(quest.getId()))) return false;
        Map<String, Long> history = completed.get(player.getUniqueId());
        var requirement = requirements.get(quest.getId());
        if (requirement != null && !requirement.isMet(player, plugin.getContractManager().getCompletedContractsCount(player.getUniqueId()))) return false;
        return NpcQuestPolicy.available(history != null, state(player, quest) != null,
                quest.isRepeatable(), history == null ? null : history.get(quest.getId()),
                quest.getCooldownMinutes(), System.currentTimeMillis());
    }

    public void startQuest(Player player, NpcQuest quest) {
        startQuest(player, quest, 0);
    }

    public void startQuest(Player player, NpcQuest quest, int initialBonusInteractions) {
        var issuer = plugin.getNpcBridge().provider().flatMap(p -> java.util.Optional.ofNullable(resolveKey(quest.getNpcKey())).flatMap(p::byKey)).orElse(null);
        if (!canInteract(player, issuer)) return;
        begin(player, quest, initialBonusInteractions);
    }

    /**
     * Takes a chain from the contract board: no NPC needs to be near. The first step of a chain is a talk with its
     * giver anyway, so the story is not skipped; every other check (availability, the limit, the database) is the same.
     */
    public void startFromBoard(Player player, NpcQuest quest) {
        if (player == null || quest == null || questMap.get(quest.getId()) != quest) return;
        begin(player, quest, 0);
    }

    private void begin(Player player, NpcQuest quest, int initialBonusInteractions) {
        if (activePlayerQuests.getOrDefault(player.getUniqueId(), Map.of()).size() + pending.stream().filter(k -> k.startsWith(player.getUniqueId() + ":")).count() >= MAX_ACTIVE_CHAINS) {
            player.sendMessage(mm.deserialize("<yellow>Сначала завершите одно из трёх активных поручений.</yellow>")); return;
        }
        if (!available(player, quest)) { player.sendMessage(mm.deserialize("<yellow>Поручение уже принято или ещё недоступно.</yellow>")); return; }
        UUID uuid = player.getUniqueId();
        String lock = lock(uuid, quest.getId());
        if (!pending.add(lock)) return;
        int friendlyBonus = "friendly".equals(mood(player)) ? 1 : 0;
        int totalInitialBonus = Math.max(0, initialBonusInteractions) + friendlyBonus;
        long now = System.currentTimeMillis();
        writer.execute(() -> {
            boolean success = false;
            try (Connection c = plugin.getDatabase().getConnection()) {
                success = NpcQuestStorage.start(c, uuid, quest.getId(), quest.isRepeatable(), quest.getCooldownMinutes(), now);
                if (success && totalInitialBonus > 0) {
                    for (int i = 0; i < totalInitialBonus; i++) {
                        NpcQuestStorage.addBonusInteraction(c, uuid, quest.getId());
                    }
                }
            } catch (SQLException error) { failure(error); }
            boolean accepted = success;
            main(() -> {
                pending.remove(lock);
                if (!accepted) { if (player.isOnline()) player.sendMessage(mm.deserialize("<red>Поручение не принято. Попробуйте позже.</red>")); return; }
                if (completed.containsKey(uuid)) activePlayerQuests.computeIfAbsent(uuid, k -> new LinkedHashMap<>())
                        .put(quest.getId(), new ActiveQuestProgress(0, 0, now, true, totalInitialBonus, 0));
                if (player.isOnline()) {
                    if (plugin.getQuestSidebar() != null) plugin.getQuestSidebar().focus(uuid, "story:" + quest.getId());
                    player.sendMessage(mm.deserialize("<green>Поручение принято: </green>" + quest.getDisplayName()));
                    showCurrentStep(player, quest);
                    plugin.getContractBoard().refresh(player);
                }
            });
        });
    }

    /** Records a polite/helpful dialogue option chosen by the player for an active quest. */
    public void recordBonusInteraction(Player player, NpcQuest quest) {
        if (player == null || quest == null) return;
        ActiveQuestProgress state = state(player, quest);
        if (state == null) return;
        state.addBonusInteraction();
        UUID uuid = player.getUniqueId();
        writer.execute(() -> {
            try (Connection c = plugin.getDatabase().getConnection()) {
                NpcQuestStorage.addBonusInteraction(c, uuid, quest.getId());
            } catch (SQLException error) { failure(error); }
        });
    }

    /** Quest interactions reserve this click before profession menus; other players are unaffected. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onNpcClick(dev.lovelace.lovecore.api.npc.NpcClickEvent event) {
        if (event.click() != dev.lovelace.lovecore.api.npc.NpcClick.RIGHT || event.npc() == null
                || !event.npc().providerId().equals(plugin.getNpcBridge().providerId())) return;
        Player player = event.getPlayer();
        if (player.isSneaking()) return; // Deliberate access to the NPC's ordinary profession.
        String key = event.npc().key();
        if (!completed.containsKey(player.getUniqueId())) {
            // Avoid opening a competing profession menu until quest state has loaded.
            if (questMap.values().stream().anyMatch(q -> matches(q.getNpcKey(), key) || q.getSteps().stream().anyMatch(s -> matches(s.getTargetNpcKey(), key)))) {
                event.setCancelled(true);
                player.sendMessage(mm.deserialize("<yellow>Данные поручений загружаются. Повторите разговор через секунду.</yellow>"));
            }
            return;
        }
        var conversations = new ArrayList<NpcQuest>();
        for (var entry : activePlayerQuests.getOrDefault(player.getUniqueId(), Map.of()).entrySet()) {
            var quest = questMap.get(entry.getKey());
            if (quest == null) continue;
            var step = quest.getStep(entry.getValue().getCurrentStep());
            if (step != null && (matches(step.getTargetNpcKey(), key)
                    || step.getTargetNpcKey() == null && entry.getValue().getStepProgress() >= step.getCount()
                    && matches(quest.getNpcKey(), key))) conversations.add(quest);
        }
        conversations.addAll(getQuestsForNpc(key).stream().filter(q -> available(player, q)).toList());
        if (conversations.isEmpty()) return;
        event.setCancelled(true);
        if (conversations.size() == 1) openNpcInteraction(player, conversations.getFirst().getId());
        else plugin.getContractBoard().openChains(player, conversations.stream().map(NpcQuest::getId).toList());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        var active = activePlayerQuests.get(uuid);
        if (active == null || active.isEmpty()) return;
        boolean anyChanged = false;
        for (ActiveQuestProgress p : active.values()) {
            if (p.isNoDeaths()) {
                p.setNoDeaths(false);
                anyChanged = true;
            }
        }
        if (anyChanged) {
            writer.execute(() -> {
                try (Connection c = plugin.getDatabase().getConnection()) {
                    NpcQuestStorage.markDeath(c, uuid);
                } catch (SQLException error) { failure(error); }
            });
        }
    }

    /** Checks active quests with a time limit and expires overdue ones without penalty. */
    public void checkExpirations() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            var active = activePlayerQuests.get(uuid);
            if (active == null || active.isEmpty()) continue;
            for (var entry : new ArrayList<>(active.entrySet())) {
                NpcQuest quest = questMap.get(entry.getKey());
                ActiveQuestProgress progress = entry.getValue();
                if (quest == null || quest.getTimeLimitMinutes() <= 0 || progress.getStartedAt() <= 0) continue;
                long deadline = progress.getStartedAt() + quest.getTimeLimitMinutes() * 60_000L;
                if (now >= deadline) {
                    expireQuest(player, quest);
                }
            }
        }
    }

    private void expireQuest(Player player, NpcQuest quest) {
        UUID uuid = player.getUniqueId();
        String lock = lock(uuid, quest.getId());
        if (!pending.add(lock)) return;
        writer.execute(() -> {
            boolean expired = false;
            try (Connection c = plugin.getDatabase().getConnection()) {
                expired = NpcQuestStorage.expire(c, uuid, quest.getId());
            } catch (SQLException error) { failure(error); }
            boolean ok = expired;
            main(() -> {
                pending.remove(lock);
                if (ok) {
                    var active = activePlayerQuests.get(uuid);
                    if (active != null) active.remove(quest.getId());
                    var history = completed.get(uuid);
                    if (history != null) history.remove(quest.getId());
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null) {
                        Notify.important(online, mm.deserialize("<yellow>Срок поручения истёк: </yellow>" + quest.getDisplayName() + " <gray>(без штрафа)</gray>"));
                        if (plugin.getContractBoard() != null) plugin.getContractBoard().refresh(online);
                    }
                }
            });
        });
    }

    /** How many chains a player may carry at once. */
    public static final int MAX_ACTIVE_CHAINS = 3;

    /** Every loaded chain with its state for this player, in the order of the definition files. */
    public List<ChainView> viewsFor(Player player) {
        List<ChainView> views = new ArrayList<>(questMap.size());
        for (NpcQuest quest : questMap.values()) views.add(viewOf(player, quest));
        return views;
    }

    public NpcQuest getQuest(String id) { return questMap.get(id); }

    public int activeCount(Player player) {
        return activePlayerQuests.getOrDefault(player.getUniqueId(), Map.of()).size();
    }

    public ChainView viewOf(Player player, NpcQuest quest) {
        var progress = state(player, quest);
        if (progress != null) {
            return new ChainView(quest, ChainStatus.ACTIVE, progress.getCurrentStep(), progress.getStepProgress(), 0, LockReason.NONE);
        }
        Map<String, Long> history = completed.get(player.getUniqueId());
        if (history == null) return new ChainView(quest, ChainStatus.LOADING, 0, 0, 0, LockReason.NONE);
        if (plugin.getContractManager().isContractsDisabled(player)) {
            return new ChainView(quest, ChainStatus.LOCKED, 0, 0, 0, LockReason.DISABLED);
        }
        if (!me.lovelace.lovecontracts.service.ContractAchievementRewards.allowed(player.getUniqueId(), requiredAchievements.get(quest.getId()))) {
            return new ChainView(quest, ChainStatus.LOCKED, 0, 0, 0, LockReason.ACHIEVEMENT);
        }
        var requirement = requirements.get(quest.getId());
        if (requirement != null && !requirement.isMet(player, plugin.getContractManager().getCompletedContractsCount(player.getUniqueId()))) {
            return new ChainView(quest, ChainStatus.LOCKED, 0, 0, 0, LockReason.REQUIREMENTS);
        }
        Long last = history.get(quest.getId());
        if (last == null) return new ChainView(quest, ChainStatus.AVAILABLE, 0, 0, 0, LockReason.NONE);
        if (!quest.isRepeatable()) return new ChainView(quest, ChainStatus.DONE, 0, 0, 0, LockReason.NONE);
        long left = last + quest.getCooldownMinutes() * 60_000L - System.currentTimeMillis();
        return left > 0
                ? new ChainView(quest, ChainStatus.COOLDOWN, 0, 0, left, LockReason.NONE)
                : new ChainView(quest, ChainStatus.AVAILABLE, 0, 0, 0, LockReason.NONE);
    }

    /** Remaining time in milliseconds for an active quest with a time limit, or 0 if unlimited. */
    public long timeLeftMillis(Player player, NpcQuest quest) {
        if (player == null || quest == null || quest.getTimeLimitMinutes() <= 0) return 0;
        ActiveQuestProgress p = state(player, quest);
        if (p == null || p.getStartedAt() <= 0) return quest.getTimeLimitMinutes() * 60_000L;
        return Math.max(0L, p.getStartedAt() + quest.getTimeLimitMinutes() * 60_000L - System.currentTimeMillis());
    }

    /** Name of the NPC a step or the whole chain points at, with its position; empty strings when it is not known. */
    public record Whereabouts(String name, String where) {
    }

    /** Whom the player has to talk to now for an active chain, or the giver of a chain that is not taken yet. */
    public Whereabouts whereabouts(Player player, NpcQuest quest) {
        String target = quest.getNpcKey();
        String fallbackName = "";
        var progress = state(player, quest);
        if (progress != null) {
            var step = quest.getStep(progress.getCurrentStep());
            if (step != null) {
                fallbackName = step.getTargetNpcName();
                if (step.getTargetNpcKey() != null) target = step.getTargetNpcKey();
            }
        }
        String key = resolveKey(target);
        var npc = plugin.getNpcBridge().provider().flatMap(p -> Optional.ofNullable(key).flatMap(p::byKey)).orElse(null);
        if (npc == null) return new Whereabouts(fallbackName, "");
        String name = npc.name() == null || npc.name().isBlank() ? fallbackName : MiniMessage.miniMessage().stripTags(npc.name());
        try {
            var at = npc.location();
            return new Whereabouts(name, at.getWorld().getName() + " " + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ());
        } catch (RuntimeException unavailable) {
            return new Whereabouts(name, "");
        }
    }

    /** Gives up an active chain: no reward, no cooldown; it can be taken again at once. */
    public void abandon(Player player, NpcQuest quest) {
        if (player == null || quest == null) return;
        UUID uuid = player.getUniqueId();
        if (state(player, quest) == null) return;
        String lock = lock(uuid, quest.getId());
        if (!pending.add(lock)) return;
        writer.execute(() -> {
            boolean saved = false;
            try (Connection c = plugin.getDatabase().getConnection()) {
                saved = NpcQuestStorage.abandon(c, uuid, quest.getId());
            } catch (SQLException error) { failure(error); }
            boolean ok = saved;
            main(() -> {
                pending.remove(lock);
                if (ok) {
                    var active = activePlayerQuests.get(uuid);
                    if (active != null) active.remove(quest.getId());
                    var history = completed.get(uuid);
                    if (history != null) history.remove(quest.getId());
                }
                Player online = Bukkit.getPlayer(uuid);
                if (online == null) return;
                online.sendMessage(mm.deserialize(ok
                        ? "<yellow>Вы отказались от поручения: </yellow>" + quest.getDisplayName()
                        : "<red>Не удалось отказаться от поручения. Повторите попытку.</red>"));
                if (plugin.getContractBoard() != null) plugin.getContractBoard().refresh(online);
            });
        });
    }

    // ------------------------------------------------------------------ admin operations

    /** What an admin operation did, as a sentence for the sender; delivered on the main thread. */
    public record AdminOutcome(boolean ok, String message) {
        static AdminOutcome ok(String message) { return new AdminOutcome(true, message); }
        static AdminOutcome fail(String message) { return new AdminOutcome(false, message); }
    }

    @FunctionalInterface
    private interface AdminSql { boolean run(Connection c) throws SQLException; }

    /**
     * Runs one admin write for a player and a chain on the quest writer, under the same lock as the game's own
     * transitions, so an admin command and a click on the NPC cannot interleave. After a change the player's state is
     * read again (online players) so memory, the board and the sidebar follow the database.
     */
    private void adminWrite(UUID uuid, String questId, AdminSql sql, String okMessage, String noChange,
                            java.util.function.Consumer<AdminOutcome> done) {
        String lock = questId == null ? null : lock(uuid, questId);
        if (lock != null && !pending.add(lock)) {
            done.accept(AdminOutcome.fail("С этой цепочкой сейчас выполняется другое действие, повторите через секунду."));
            return;
        }
        writer.execute(() -> {
            boolean changed = false;
            boolean failed = false;
            try (Connection c = plugin.getDatabase().getConnection()) {
                changed = sql.run(c);
            } catch (SQLException error) {
                failure(error);
                failed = true;
            }
            boolean ok = changed, err = failed;
            main(() -> {
                if (lock != null) pending.remove(lock);
                if (err) { done.accept(AdminOutcome.fail("Ошибка базы данных, подробности в консоли.")); return; }
                if (!ok) { done.accept(AdminOutcome.fail(noChange)); return; }
                plugin.getContractManager().invalidateCompletedCount(uuid);
                if (Bukkit.getPlayer(uuid) != null) loadPlayerActiveQuestsFromDb(uuid);
                done.accept(AdminOutcome.ok(okMessage));
            });
        });
    }

    /** Puts the chain in progress at its first step: no NPC, no cooldown, no one-time rule and no limit of three chains. */
    public void adminStart(UUID uuid, NpcQuest quest, java.util.function.Consumer<AdminOutcome> done) {
        adminWrite(uuid, quest.getId(), c -> { NpcQuestStorage.forceStart(c, uuid, quest.getId(), System.currentTimeMillis()); return true; },
                "Цепочка запущена с первого этапа.", "Не удалось запустить цепочку.", done);
    }

    /** Erases one chain of the player (progress, history, cooldown), or all of them when {@code questId} is null. */
    public void adminClear(UUID uuid, String questId, java.util.function.Consumer<AdminOutcome> done) {
        adminWrite(uuid, questId, c -> (questId == null
                        ? NpcQuestStorage.clearAll(c, uuid)
                        : NpcQuestStorage.clear(c, uuid, questId)) > 0,
                questId == null ? "Все записи о цепочках игрока стёрты." : "Запись о цепочке стёрта: её можно проходить заново.",
                "У игрока нет такой записи.", done);
    }

    /** Gives the chain up as the player would: no reward, no cooldown, it can be taken again at once. */
    public void adminFail(UUID uuid, NpcQuest quest, java.util.function.Consumer<AdminOutcome> done) {
        adminWrite(uuid, quest.getId(), c -> NpcQuestStorage.abandon(c, uuid, quest.getId()),
                "Цепочка провалена (без награды и кулдауна).", "Цепочка у игрока не активна.", done);
    }

    /** Moves an active chain to the given step (1-based) and resets that step's progress. */
    public void adminSetStep(UUID uuid, NpcQuest quest, int stepNumber, java.util.function.Consumer<AdminOutcome> done) {
        if (stepNumber < 1 || stepNumber > quest.TotalSteps()) {
            done.accept(AdminOutcome.fail("Этап должен быть от 1 до " + quest.TotalSteps() + "."));
            return;
        }
        adminWrite(uuid, quest.getId(), c -> NpcQuestStorage.setStep(c, uuid, quest.getId(), stepNumber - 1),
                "Цепочка переведена на этап " + stepNumber + ".", "Цепочка у игрока не активна.", done);
    }

    /**
     * Completes the chain at once, whatever its state. The reward goes to the player when {@code reward} is set and the
     * player is online (an offline player cannot be paid from here, which the answer says); the income limit is not used.
     */
    public void adminComplete(UUID uuid, NpcQuest quest, boolean reward, java.util.function.Consumer<AdminOutcome> done) {
        long amount = reward && plugin.getConfig().getBoolean("rewards.money.enabled", true)
                ? Math.round(quest.getRewards().stream().mapToDouble(r -> r.getAmount()).sum()) : 0;
        String achievement = completionAchievements.get(quest.getId());
        adminWrite(uuid, quest.getId(), c -> {
            NpcQuestStorage.forceComplete(c, uuid, quest.getId(), quest.TotalSteps(), achievement, System.currentTimeMillis());
            return true;
        }, "Цепочка завершена.", "Не удалось завершить цепочку.", outcome -> {
            if (outcome.ok() && reward) {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    var contract = new me.lovelace.lovecontracts.model.Contract(quest.getId(), quest.getDisplayName(), "", quest.getTier(),
                            me.lovelace.lovecontracts.model.ContractType.REPEATING, -1, 1, 1, 1440, false, true, quest.getRewards(), List.of());
                    plugin.getRewardProcessor().giveRewards(online, contract, amount);
                    done.accept(AdminOutcome.ok("Цепочка завершена, награда выдана."));
                    return;
                }
                done.accept(AdminOutcome.ok("Цепочка завершена, но игрок не в сети: награда не выдана."));
                return;
            }
            done.accept(outcome);
        });
    }

    /**
     * All stored chain rows of the player for the admin overview; read from the database, so offline players work.
     * The callback gets {@code null} when the database could not be read: that is an error, not an empty list.
     */
    public void adminInspect(UUID uuid, java.util.function.Consumer<List<NpcQuestStorage.Row>> done) {
        writer.execute(() -> {
            List<NpcQuestStorage.Row> rows = null;
            try (Connection c = plugin.getDatabase().getConnection()) {
                rows = NpcQuestStorage.inspect(c, uuid);
            } catch (SQLException error) { failure(error); }
            var result = rows;
            main(() -> done.accept(result));
        });
    }

    public boolean isActive(Player player, NpcQuest quest) { return state(player, quest) != null; }

    /** A character can discuss several ongoing stories and offer new work in the same menu. */
    public void openNpcInteraction(Player player, String id) {
        var quest = questMap.get(id);
        if (quest == null) return;
        var progress = state(player, quest);
        if (progress == null) { openOffer(player, id); return; }
        var step = quest.getStep(progress.getCurrentStep());
        if (step == null) return;
        String target = step.getTargetNpcKey();
        if (target == null) {
            if (progress.getStepProgress() < step.getCount()) return;
            target = quest.getNpcKey();
        }
        String key = resolveKey(target);
        var npc = plugin.getNpcBridge().provider().flatMap(p -> Optional.ofNullable(key).flatMap(p::byKey)).orElse(null);
        if (!canInteract(player, npc)) return;
        if (plugin.getQuestSidebar() != null) plugin.getQuestSidebar().focus(player.getUniqueId(), "story:" + quest.getId());
        if (step.getTargetNpcKey() == null) { advanceStep(player, quest, List.of()); return; }
        NpcDialogueNode node = step.dialogue(mood(player));
        if (node == null) node = new NpcDialogueNode("visit", List.of(step.getDescription()), List.of(
                new NpcDialogueOption(step.getType() == NpcQuestStep.Type.DELIVER_ITEM ? "Передать припасы" : "Обсудить поручение", NpcDialogueOption.Action.COMPLETE_STEP, null),
                new NpcDialogueOption("Позже", NpcDialogueOption.Action.CLOSE, null)));
        plugin.getNpcDialogueGUI().open(player, quest, node, npc, progress.getCurrentStep());
    }

    public void openOffer(Player player, String id) {
        NpcQuest quest = questMap.get(id);
        if (quest == null || !available(player, quest)) return;
        var npc = plugin.getNpcBridge().provider().flatMap(p -> java.util.Optional.ofNullable(resolveKey(quest.getNpcKey())).flatMap(p::byKey)).orElse(null);
        if (!canInteract(player, npc)) { player.sendMessage(mm.deserialize("<yellow>Подойдите к заказчику этого поручения.</yellow>")); return; }
        String mood = mood(player);
        NpcDialogueNode node = quest.getDialogueNode(mood);
        if (node == null) node = quest.getDialogueNode(quest.getInitialDialogueNode());
        plugin.getNpcDialogueGUI().open(player, quest, node, npc, -1);
    }

    private String mood(Player player) {
        String mood = "neutral";
        var bridge = new me.lovelace.lovecontracts.player.integration.LoveCoreBridge();
        if (bridge.playstyleLevel(player.getUniqueId()) == 0) mood = "fearful";
        else if (bridge.politenessLevel(player.getUniqueId()) >= 0 && bridge.politenessLevel(player.getUniqueId()) <= 1) mood = "skeptical";
        else if (bridge.politenessLevel(player.getUniqueId()) >= 5 || bridge.isKindPlaystyle(player.getUniqueId())) mood = "friendly";
        return mood;
    }

    public void confirmStep(Player player, NpcQuest quest, int expected, dev.lovelace.lovecore.api.npc.NpcHandle npc) {
        ActiveQuestProgress state = state(player, quest);
        if (questMap.get(quest.getId()) != quest || state == null || state.getCurrentStep() != expected || !canInteract(player, npc)) return;
        NpcQuestStep step = quest.getStep(expected);
        if (step == null || !matches(step.getTargetNpcKey(), npc.key())) return;
        List<org.bukkit.inventory.ItemStack> taken = new ArrayList<>();
        if (step.getType() == NpcQuestStep.Type.DELIVER_ITEM) {
            int count = 0;
            for (var item : player.getInventory().getStorageContents())
                if (item != null && item.getType() == step.getMaterial() && !item.hasItemMeta()) count += item.getAmount();
            if (count < step.getCount()) { player.sendMessage(mm.deserialize("<yellow>Нужны обычные предметы: " + step.getMaterial() + " × " + step.getCount() + ".</yellow>")); return; }
            if (pending.contains(lock(player.getUniqueId(), quest.getId()))) return;
            int surplus = Math.max(0, count - step.getCount());
            int remaining = step.getCount();
            var storage = player.getInventory().getStorageContents();
            for (int i = 0; i < storage.length && remaining > 0; i++) {
                var item = storage[i];
                if (item == null || item.getType() != step.getMaterial() || item.hasItemMeta()) continue;
                int amount = Math.min(item.getAmount(), remaining);
                taken.add(new org.bukkit.inventory.ItemStack(item.getType(), amount));
                remaining -= amount;
                item.setAmount(item.getAmount() - amount);
                if (item.getAmount() == 0) storage[i] = null;
            }
            player.getInventory().setStorageContents(storage);
            if (surplus > 0) {
                state.addSurplusDelivered(surplus);
                UUID uuid = player.getUniqueId();
                writer.execute(() -> {
                    try (Connection c = plugin.getDatabase().getConnection()) {
                        NpcQuestStorage.addSurplus(c, uuid, quest.getId(), surplus);
                    } catch (SQLException error) { failure(error); }
                });
            }
        } else if (step.getType() != NpcQuestStep.Type.VISIT_NPC) return;
        advanceStep(player, quest, taken);
    }

    private void advanceStep(Player player, NpcQuest quest, List<org.bukkit.inventory.ItemStack> taken) {
        ActiveQuestProgress state = state(player, quest);
        if (state == null) return;
        UUID uuid = player.getUniqueId();
        String lock = lock(uuid, quest.getId());
        if (!pending.add(lock)) return;
        int expected = state.getCurrentStep(), next = expected + 1;
        boolean finish = next == quest.TotalSteps();
        long baseReward = plugin.getConfig().getBoolean("rewards.money.enabled", true)
                ? Math.round(quest.getRewards().stream().mapToDouble(Reward::getAmount).sum()) : 0;
        long now = System.currentTimeMillis();
        RewardTiers.Calculation calc = finish
                ? quest.getRewardTiers().calculate(
                        baseReward,
                        state.getStartedAt() > 0 ? Math.max(0, now - state.getStartedAt()) : -1L,
                        quest.getTimeLimitMinutes(),
                        state.getBonusInteractions(),
                        state.isNoDeaths(),
                        state.getSurplusDelivered(),
                        plugin.getConfig().getDouble("economy.reward-tiers.max-multiplier", 1.5))
                : new RewardTiers.Calculation(baseReward, 1.0, List.of());
        long reward = calc.finalReward();
        long limit = plugin.getConfig().getLong("economy.contracts-v2.rolling-income-limit", 1800);
        writer.execute(() -> {
            boolean success = false, budget = true;
            try (Connection c = plugin.getDatabase().getConnection()) {
                var result = NpcQuestStorage.advance(c, uuid, quest.getId(), expected, next, finish, reward, limit, now, completionAchievements.get(quest.getId()));
                success = result == NpcQuestStorage.Result.SAVED;
                budget = result != NpcQuestStorage.Result.INCOME_LIMIT;
            } catch (SQLException error) { failure(error); }
            if (!success && !budget) {
                try (Connection c = plugin.getDatabase().getConnection(); var q = c.prepareStatement("UPDATE player_npc_quests SET step_progress=MAX(step_progress,?) WHERE player_uuid=? AND quest_id=? AND current_step=? AND status='ACTIVE'")) {
                    q.setInt(1, state.getStepProgress()); q.setString(2, uuid.toString()); q.setString(3, quest.getId()); q.setInt(4, expected); q.executeUpdate();
                } catch (SQLException error) { failure(error); }
            }
            boolean saved = success, allowed = budget;
            main(() -> {
                pending.remove(lock);
                if (!saved) {
                    Player recipient = Bukkit.getPlayer(uuid);
                    if (recipient == null) recipient = player;
                    for (var stack : taken) for (var overflow : recipient.getInventory().addItem(stack).values()) recipient.getWorld().dropItemNaturally(recipient.getLocation(), overflow);
                    if (!recipient.isOnline() && !taken.isEmpty()) recipient.saveData();
                    if (recipient.isOnline()) recipient.sendMessage(mm.deserialize(allowed ? "<red>Не удалось сохранить этап. Повторите попытку.</red>" : "<yellow>Достигнут лимит заработка за 24 часа. Этап сохранён: сдайте его позже, награда не потеряна.</yellow>"));
                    return;
                }
                var onlineState = activePlayerQuests.getOrDefault(uuid, Map.of());
                if (finish) {
                    plugin.getContractManager().invalidateCompletedCount(uuid);
                    plugin.getStatBus().ifPresent(bus -> bus.record(uuid, "contracts.completed", 1));
                    if (activePlayerQuests.containsKey(uuid)) onlineState.remove(quest.getId());
                    if (completed.containsKey(uuid)) completed.get(uuid).put(quest.getId(), now);
                    var contract = new me.lovelace.lovecontracts.model.Contract(quest.getId(), quest.getDisplayName(), "", quest.getTier(),
                            me.lovelace.lovecontracts.model.ContractType.REPEATING, -1, 1, 1, 1440, false, true, quest.getRewards(), List.of());
                    plugin.getRewardProcessor().giveRewards(player, contract, reward);
                    if (player.isOnline()) {
                        String bonusNote = calc.breakdown().isEmpty() ? ""
                                : " <gray>(" + String.join(", ", calc.breakdown()) + ")</gray>";
                        player.sendMessage(mm.deserialize("<green>Поручение выполнено: </green>" + quest.getDisplayName() + bonusNote));
                    }
                } else {
                    ActiveQuestProgress current = onlineState.get(quest.getId());
                    if (current != null) { current.setCurrentStep(next); current.setStepProgress(0); }
                    if (player.isOnline()) showCurrentStep(player, quest);
                }
                if (player.isOnline()) plugin.getContractBoard().refresh(player);
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        var result = event.getRecipe().getResult();
        int amount = result.getAmount();
        if (event.isShiftClick()) {
            int crafts = Integer.MAX_VALUE;
            for (var ingredient : event.getInventory().getMatrix())
                if (ingredient != null && !ingredient.getType().isAir()) crafts = Math.min(crafts, ingredient.getAmount());
            int capacity = 0;
            for (var item : player.getInventory().getStorageContents()) {
                if (item == null || item.getType().isAir()) capacity += result.getMaxStackSize();
                else if (item.isSimilar(result)) capacity += Math.max(0, result.getMaxStackSize() - item.getAmount());
            }
            if (crafts == Integer.MAX_VALUE) return;
            amount = Math.min(crafts, capacity / result.getAmount()) * result.getAmount();
        } else if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
            var destination = player.getInventory().getItem(event.getHotbarButton());
            if (destination != null && !destination.getType().isAir()) return;
        } else {
            var cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && (!cursor.isSimilar(result) || cursor.getAmount() + result.getAmount() > result.getMaxStackSize())) return;
        }
        if (amount > 0) checkProgress(player, NpcQuestStep.Type.CRAFT_ITEM, result.getType(), null, amount);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMine(BlockBreakEvent event) {
        if (!plugin.getPlacedBlockTracker().isPlaced(event.getBlock())) checkProgress(event.getPlayer(), NpcQuestStep.Type.MINE_MATERIAL, event.getBlock().getType(), null, 1);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() != null) checkProgress(event.getEntity().getKiller(), NpcQuestStep.Type.KILL_ENTITY, null, event.getEntityType(), 1);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(org.bukkit.event.player.PlayerFishEvent event) {
        if (event.getState() == org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH
                && event.getCaught() instanceof org.bukkit.entity.Item)
            checkProgress(event.getPlayer(), NpcQuestStep.Type.CATCH_FISH, null, null, 1);
    }

    private void checkProgress(Player player, NpcQuestStep.Type type, Material material, EntityType entity, int delta) {
        checkProgress(player, type, material, entity, delta, player.getLocation());
    }

    private void checkProgress(Player player, NpcQuestStep.Type type, Material material, EntityType entity, int delta, org.bukkit.Location at) {
        for (var entry : new ArrayList<>(activePlayerQuests.getOrDefault(player.getUniqueId(), Map.of()).entrySet())) {
            NpcQuest quest = questMap.get(entry.getKey());
            var state = entry.getValue();
            if (quest == null || pending.contains(lock(player.getUniqueId(), quest.getId()))) continue;
            NpcQuestStep step = quest.getStep(state.getCurrentStep());
            if (step != null && step.getLocation() != null && !step.getLocation().contains(at.getWorld().getName(), at.getX(), at.getY(), at.getZ())) continue;
            if (step == null || step.getType() != type || material != null && step.getMaterial() != material || entity != null && step.getEntityType() != entity) continue;
            state.setStepProgress(Math.min(step.getCount(), state.getStepProgress() + delta));
            if (state.getStepProgress() >= step.getCount()) advanceStep(player, quest, List.of());
            else {
                int progress = state.getStepProgress(), expected = state.getCurrentStep();
                writer.execute(() -> {
                    try (Connection c = plugin.getDatabase().getConnection(); var q = c.prepareStatement("UPDATE player_npc_quests SET step_progress=MAX(step_progress,?) WHERE player_uuid=? AND quest_id=? AND current_step=? AND status='ACTIVE'")) {
                        q.setInt(1, progress); q.setString(2, player.getUniqueId().toString()); q.setString(3, quest.getId()); q.setInt(4, expected); q.executeUpdate();
                    } catch (SQLException error) { failure(error); }
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getWorld().equals(event.getTo().getWorld())
                && event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        checkProgress(event.getPlayer(), NpcQuestStep.Type.VISIT_LOCATION, null, null, 1, event.getTo());
    }

    /** In-memory snapshot for LE and PAPI; no database reads on the scoreboard timer or main thread. */
    public List<me.lovelace.lovecontracts.service.QuestSidebarText.Objective> objectives(Player player) {
        var result = new ArrayList<me.lovelace.lovecontracts.service.QuestSidebarText.Objective>();
        for (var entry : activePlayerQuests.getOrDefault(player.getUniqueId(), Map.of()).entrySet()) {
            var quest = questMap.get(entry.getKey());
            if (quest == null) continue;
            var progress = entry.getValue();
            var step = quest.getStep(progress.getCurrentStep());
            if (step == null) continue;
            int shown = progress.getStepProgress();
            if (step.getType() == NpcQuestStep.Type.DELIVER_ITEM) {
                shown = 0;
                for (var item : player.getInventory().getStorageContents())
                    if (item != null && item.getType() == step.getMaterial() && !item.hasItemMeta()) shown += item.getAmount();
                shown = Math.min(shown, step.getCount());
            }
            long remMillis = timeLeftMillis(player, quest);
            String timeLeft = quest.getTimeLimitMinutes() > 0 ? DurationText.format(remMillis) : "";
            long baseReward = Math.round(quest.getRewards().stream().mapToDouble(Reward::getAmount).sum());
            String rewardText = baseReward > 0 ? CoinFormat.format(baseReward) : "";
            result.add(new me.lovelace.lovecontracts.service.QuestSidebarText.Objective(
                    "story:" + quest.getId(),
                    me.lovelace.lovecontracts.service.QuestSidebar.plain(quest.getDisplayName()),
                    me.lovelace.lovecontracts.service.QuestSidebar.plain(step.getDescription()),
                    progress.getCurrentStep() + 1,
                    quest.TotalSteps(),
                    shown,
                    step.getCount(),
                    timeLeft,
                    rewardText));
        }
        return result;
    }

    private void showCurrentStep(Player player, NpcQuest quest) {
        var progress = state(player, quest);
        if (progress == null) return;
        var step = quest.getStep(progress.getCurrentStep());
        if (step != null) player.sendMessage(mm.deserialize("<yellow>Этап " + (progress.getCurrentStep() + 1)
                + "/" + quest.TotalSteps() + ":</yellow> " + step.getDescription()));
    }

    public void close() {
        writer.shutdown();
        try { if (!writer.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) writer.shutdownNow(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); writer.shutdownNow(); }
    }
}
