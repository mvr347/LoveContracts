package me.lovelace.lovecontracts.npc.manager;

import me.lovelace.lovecontracts.LoveContracts;
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
import org.bukkit.event.inventory.CraftItemEvent;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

public class NpcQuestManager implements Listener {

    private final LoveContracts plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<String, NpcQuest> questMap = new LinkedHashMap<>();
    private final Map<Integer, List<NpcQuest>> npcQuestMap = new HashMap<>();

    public NpcQuestManager(LoveContracts plugin, NpcQuestDatabase db) {
        this.plugin = plugin;
    }

    public void loadFromConfig() {
        questMap.clear();
        npcQuestMap.clear();

        File file = new File(plugin.getDataFolder(), "npc_quests.yml");
        if (!file.exists()) {
            plugin.saveResource("npc_quests.yml", false);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("npc_quests");
        if (root == null) {
            plugin.getLogger().warning("npc_quests.yml has no 'npc_quests' section");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            try {
                NpcQuest quest = parseQuest(id, sec);
                questMap.put(id, quest);
                npcQuestMap.computeIfAbsent(quest.getNpcId(), k -> new ArrayList<>()).add(quest);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load NPC quest '" + id + "'", e);
            }
        }

        plugin.getLogger().info("Loaded " + questMap.size() + " NPC chain quest definitions");
    }

    private NpcQuest parseQuest(String id, ConfigurationSection sec) {
        int npcId = sec.getInt("npc-id", 0);
        String name = sec.getString("display-name", id);
        boolean repeatable = sec.getBoolean("repeatable", true);
        int cooldown = sec.getInt("cooldown-minutes", 0);
        String initialDialogue = sec.getString("initial-dialogue", "greeting");

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

        return new NpcQuest(id, npcId, name, repeatable, cooldown, initialDialogue, dialogues, steps, parseRewards(sec.getConfigurationSection("rewards")));
    }

    private NpcDialogueNode parseDialogueNode(String nodeId, ConfigurationSection sec) {
        List<String> lines = sec.getStringList("text");
        List<NpcDialogueOption> options = new ArrayList<>();
        for (Map<?, ?> optMap : sec.getMapList("options")) {
            String text = getMapString(optMap, "text", "");
            String actionStr = getMapString(optMap, "action", "CLOSE");
            String nextNode = optMap.containsKey("next-node") ? getMapString(optMap, "next-node", null) : null;
            NpcDialogueOption.Action action;
            try {
                action = NpcDialogueOption.Action.valueOf(actionStr);
            } catch (Exception e) {
                action = NpcDialogueOption.Action.CLOSE;
            }
            options.add(new NpcDialogueOption(text, action, nextNode));
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
        NpcQuestStep.Type type = NpcQuestStep.Type.valueOf(typeStr);
        String matStr = getMapString(map, "material", null);
        Material mat = matStr != null ? Material.valueOf(matStr) : null;
        String entStr = getMapString(map, "entity-type", null);
        EntityType ent = entStr != null ? EntityType.valueOf(entStr) : null;
        int targetNpcId = Integer.parseInt(getMapString(map, "target-npc-id", "0"));
        String targetNpcName = getMapString(map, "target-npc-name", "");
        int count = Integer.parseInt(getMapString(map, "count", "1"));
        String desc = getMapString(map, "description", "");

        NpcDialogueNode visitDialogue = null;
        if (map.get("dialogue") instanceof Map<?, ?> dMap) {
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
                        String act = getMapString(om, "action", "COMPLETE_STEP");
                        options.add(new NpcDialogueOption(text, NpcDialogueOption.Action.valueOf(act), null));
                    }
                }
            }
            visitDialogue = new NpcDialogueNode("visit", lines, options);
        }

        return new NpcQuestStep(number, type, mat, ent, targetNpcId, targetNpcName, count, desc, visitDialogue);
    }

    private List<me.lovelace.lovecontracts.model.Reward> parseRewards(ConfigurationSection sec) {
        List<me.lovelace.lovecontracts.model.Reward> rewards = new ArrayList<>();
        if (sec == null) return rewards;
        if (sec.contains("money")) {
            rewards.add(me.lovelace.lovecontracts.model.Reward.money(sec.getDouble("money")));
        }
        if (sec.contains("experience")) {
            rewards.add(new me.lovelace.lovecontracts.model.Reward(me.lovelace.lovecontracts.model.Reward.Type.EXPERIENCE, sec.getDouble("experience")));
        }
        return rewards;
    }

    public List<NpcQuest> getQuestsForNpc(int npcId) {
        return npcQuestMap.getOrDefault(npcId, List.of());
    }

    public void startQuest(Player player, NpcQuest quest) {
        if (player == null || quest == null) return;
        UUID uuid = player.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO player_npc_quests (player_uuid, quest_id, current_step, step_progress, status)
                     VALUES (?, ?, 0, 0, 'ACTIVE')
                     ON CONFLICT(player_uuid, quest_id) DO UPDATE SET
                     current_step = 0, step_progress = 0, status = 'ACTIVE'
                     """)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, quest.getId());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to start NPC quest", e);
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(mm.deserialize("<green>Контракт принят:</green> <gold>" + quest.getDisplayName() + "</gold>"));
                NpcQuestStep step = quest.getStep(0);
                if (step != null) {
                    player.sendMessage(mm.deserialize("<yellow>Этап 1:</yellow> <white>" + step.getDescription() + "</white>"));
                }
            });
        });
    }

    public void advanceStep(Player player, NpcQuest quest) {
        if (player == null || quest == null) return;
        UUID uuid = player.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int currentStep = 0;
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT current_step FROM player_npc_quests WHERE player_uuid = ? AND quest_id = ? AND status = 'ACTIVE'")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, quest.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) currentStep = rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to fetch quest step", e);
            }

            int nextStep = currentStep + 1;
            if (nextStep >= quest.TotalSteps()) {
                completeQuest(player, quest);
                return;
            }

            int finalNextStep = nextStep;
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE player_npc_quests SET current_step = ?, step_progress = 0 WHERE player_uuid = ? AND quest_id = ?")) {
                ps.setInt(1, finalNextStep);
                ps.setString(2, uuid.toString());
                ps.setString(3, quest.getId());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to advance quest step", e);
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                NpcQuestStep step = quest.getStep(finalNextStep);
                if (step != null) {
                    player.sendMessage(mm.deserialize("<green>Этап выполнен!</green> <yellow>Следующий этап:</yellow> <white>" + step.getDescription() + "</white>"));
                }
            });
        });
    }

    private void completeQuest(Player player, NpcQuest quest) {
        UUID uuid = player.getUniqueId();
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 UPDATE player_npc_quests SET status = 'COMPLETED', last_completed_at = CURRENT_TIMESTAMP
                 WHERE player_uuid = ? AND quest_id = ?
                 """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, quest.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to complete NPC quest", e);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getRewardProcessor().giveRewards(player, new me.lovelace.lovecontracts.model.Contract(
                    quest.getId(), quest.getDisplayName(), "", me.lovelace.lovecontracts.model.Difficulty.EASY,
                    me.lovelace.lovecontracts.model.ContractType.REPEATING, -1, 1, 1, 1440, false, true, quest.getRewards(), List.of()
            ));
            player.sendMessage(mm.deserialize("<green>Поздравляем! Контракт <gold>" + quest.getDisplayName() + "</gold> успешно выполнен!</green>"));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Material mat = event.getRecipe().getResult().getType();
        int amount = event.getRecipe().getResult().getAmount();
        checkProgress(player, NpcQuestStep.Type.CRAFT_ITEM, mat, null, amount);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMine(BlockBreakEvent event) {
        Player player = event.getPlayer();
        checkProgress(player, NpcQuestStep.Type.MINE_MATERIAL, event.getBlock().getType(), null, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        checkProgress(killer, NpcQuestStep.Type.KILL_ENTITY, null, event.getEntityType(), 1);
    }

    private void checkProgress(Player player, NpcQuestStep.Type stepType, Material mat, EntityType ent, int delta) {
        UUID uuid = player.getUniqueId();
        for (NpcQuest quest : questMap.values()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (Connection conn = plugin.getDatabase().getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT current_step, step_progress FROM player_npc_quests WHERE player_uuid = ? AND quest_id = ? AND status = 'ACTIVE'")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, quest.getId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int stepIdx = rs.getInt(1);
                            int progress = rs.getInt(2);
                            NpcQuestStep step = quest.getStep(stepIdx);
                            if (step != null && step.getType() == stepType) {
                                boolean matchMat = (mat == null || step.getMaterial() == mat);
                                boolean matchEnt = (ent == null || step.getEntityType() == ent);
                                if (matchMat && matchEnt) {
                                    int newProg = progress + delta;
                                    if (newProg >= step.getCount()) {
                                        advanceStep(player, quest);
                                    } else {
                                        updateProgress(uuid, quest.getId(), newProg);
                                    }
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "checkProgress failed", e);
                }
            });
        }
    }

    private void updateProgress(UUID uuid, String questId, int progress) {
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE player_npc_quests SET step_progress = ? WHERE player_uuid = ? AND quest_id = ?")) {
            ps.setInt(1, progress);
            ps.setString(2, uuid.toString());
            ps.setString(3, questId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "updateProgress failed", e);
        }
    }
}
