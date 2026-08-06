package me.lovelace.lovecontracts;

import me.lovelace.lovecontracts.command.ContractCommand;
import me.lovelace.lovecontracts.command.LoveContractsAdminCommand;
import me.lovelace.lovecontracts.gui.ContractConfirmGUI;
import me.lovelace.lovecontracts.gui.ContractGUI;
import me.lovelace.lovecontracts.integration.CitizensIntegration;
import me.lovelace.lovecontracts.listener.ContractNpcListener;
import me.lovelace.lovecontracts.listener.ContractSignListener;
import me.lovelace.lovecontracts.manager.ContractManager;
import me.lovelace.lovecontracts.manager.ContractRegistry;
import me.lovelace.lovecontracts.manager.MessageManager;
import me.lovelace.lovecontracts.manager.RewardProcessor;
import me.lovelace.lovecontracts.manager.SyncManager;
import me.lovelace.lovecontracts.player.PlayerContractCommand;
import me.lovelace.lovecontracts.player.PlayerContractListener;
import me.lovelace.lovecontracts.player.gui.PlayerContractBoardGUI;
import me.lovelace.lovecontracts.player.gui.PlayerContractMyGUI;
import me.lovelace.lovecontracts.player.integration.LoveCoreBridge;
import me.lovelace.lovecontracts.player.manager.PlayerContractManager;
import me.lovelace.lovecontracts.player.storage.PlayerContractDatabase;
import me.lovelace.lovecontracts.player.task.PlayerContractExpirationTask;
import me.lovelace.lovecontracts.service.ContractPlaceholderExpansion;
import me.lovelace.lovecontracts.storage.ContractDatabase;
import me.lovelace.lovecontracts.task.ContractExpirationTask;
import me.lovelace.lovecontracts.task.ContractRotationTask;
import dev.lovelace.lovecore.api.stats.StatBus;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Calendar;
import java.util.Optional;

public final class LoveContracts extends JavaPlugin {

    private static LoveContracts instance;

    private ContractDatabase database;
    private ContractRegistry registry;
    private ContractManager contractManager;
    private me.lovelace.lovecontracts.manager.ContractSignManager signManager;
    private RewardProcessor rewardProcessor;
    private SyncManager syncManager;
    private ContractGUI contractGUI;
    private ContractConfirmGUI confirmGUI;
    private me.lovelace.lovecontracts.gui.ContractCreateGUI contractCreateGUI;
    private ContractRotationTask rotationTask;
    private ContractExpirationTask expirationTask;
    private me.lovelace.lovecontracts.manager.FineManager fineManager;
    private MessageManager messageManager;
    private Optional<StatBus> statBus = Optional.empty();

    private PlayerContractDatabase playerContractDatabase;
    private PlayerContractManager playerContractManager;
    private PlayerContractBoardGUI playerContractBoardGUI;
    private PlayerContractMyGUI playerContractMyGUI;
    private PlayerContractExpirationTask playerContractExpirationTask;

    private me.lovelace.lovecontracts.npc.storage.NpcQuestDatabase npcQuestDatabase;
    private me.lovelace.lovecontracts.npc.manager.NpcQuestManager npcQuestManager;
    private me.lovelace.lovecontracts.npc.gui.NpcDialogueGUI npcDialogueGUI;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResource("contracts.yml", false);
        saveResource("messages.yml", false);
        saveResource("npc_quests.yml", false);
        saveResource("heads.yml", false);
        loadHeadsConfig();

        try {
            database = new ContractDatabase(this);
            database.initialize();
        } catch (Exception e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        try {
            playerContractDatabase = new PlayerContractDatabase(this);
            playerContractDatabase.initialize();
        } catch (Exception e) {
            getLogger().severe("Failed to initialize player contract tables: " + e.getMessage());
            e.printStackTrace();
        }
        playerContractManager = new PlayerContractManager(this, playerContractDatabase, new LoveCoreBridge());
        playerContractBoardGUI = new PlayerContractBoardGUI(this, playerContractManager);
        playerContractMyGUI = new PlayerContractMyGUI(this, playerContractManager);

        npcQuestDatabase = new me.lovelace.lovecontracts.npc.storage.NpcQuestDatabase(this);
        npcQuestDatabase.initialize();
        npcQuestManager = new me.lovelace.lovecontracts.npc.manager.NpcQuestManager(this, npcQuestDatabase);
        npcQuestManager.loadFromConfig();
        npcDialogueGUI = new me.lovelace.lovecontracts.npc.gui.NpcDialogueGUI(this);

        messageManager = new MessageManager(this);
        registry = new ContractRegistry(this);
        registry.loadFromConfig();

        statBus = Optional.ofNullable(Bukkit.getServicesManager().load(StatBus.class));
        if (statBus.isPresent()) {
            getLogger().info("LoveCore StatBus hooked for metric reporting");
        }

        fineManager = new me.lovelace.lovecontracts.manager.FineManager(this);
        rewardProcessor = new RewardProcessor(this);
        syncManager = new SyncManager(this);
        contractManager = new ContractManager(this);
        signManager = new me.lovelace.lovecontracts.manager.ContractSignManager(this);
        contractGUI = new ContractGUI(this);
        confirmGUI = new ContractConfirmGUI(this);
        contractCreateGUI = new me.lovelace.lovecontracts.gui.ContractCreateGUI(this);

        PluginCommand contractsCmd = getCommand("contracts");
        if (contractsCmd != null) {
            ContractCommand executor = new ContractCommand(this);
            contractsCmd.setExecutor(executor);
            contractsCmd.setTabCompleter(executor);
        }

        PluginCommand adminCmd = getCommand("lovecontracts");
        if (adminCmd != null) {
            LoveContractsAdminCommand admin = new LoveContractsAdminCommand(this);
            adminCmd.setExecutor(admin);
            adminCmd.setTabCompleter(admin);
        }

        PluginCommand pcontractCmd = getCommand("pcontract");
        if (pcontractCmd != null) {
            PlayerContractCommand executor = new PlayerContractCommand(
                    this, playerContractManager, playerContractBoardGUI, playerContractMyGUI);
            pcontractCmd.setExecutor(executor);
            pcontractCmd.setTabCompleter(executor);
        }

        Bukkit.getPluginManager().registerEvents(new me.lovelace.lovecontracts.listener.PlayerQuitListener(this), this);
        Bukkit.getPluginManager().registerEvents(new me.lovelace.lovecontracts.listener.FineCollectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ContractSignListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ContractNpcListener(this, new CitizensIntegration()), this);
        Bukkit.getPluginManager().registerEvents(contractGUI, this);
        Bukkit.getPluginManager().registerEvents(confirmGUI, this);
        Bukkit.getPluginManager().registerEvents(contractCreateGUI, this);
        Bukkit.getPluginManager().registerEvents(playerContractBoardGUI, this);
        Bukkit.getPluginManager().registerEvents(playerContractMyGUI, this);
        Bukkit.getPluginManager().registerEvents(npcDialogueGUI, this);
        Bukkit.getPluginManager().registerEvents(npcQuestManager, this);
        Bukkit.getPluginManager().registerEvents(new PlayerContractListener(playerContractManager), this);

        signManager.updateAllSigns();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ContractPlaceholderExpansion(this).register();
            getLogger().info("PlaceholderAPI hooked");
        }

        rotationTask = new ContractRotationTask(this);
        scheduleRotationTask();
        expirationTask = new ContractExpirationTask(this);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, expirationTask, 20L * 60, 20L * 60);

        contractManager.startRotationTask();

        playerContractExpirationTask = new PlayerContractExpirationTask(playerContractManager);
        long pcontractIntervalTicks = 20L * 60 * getConfig().getInt("player-contracts.expiration-check-interval-minutes", 5);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, playerContractExpirationTask,
                pcontractIntervalTicks, pcontractIntervalTicks);

        getLogger().info("LoveContracts v" + getPluginMeta().getVersion() + " enabled — "
                + registry.size() + " contracts loaded");
    }

    private void scheduleRotationTask() {
        String rotationTime = getConfig().getString("rotation.time", "00:00");
        try {
            String[] parts = rotationTime.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            Calendar now = Calendar.getInstance();
            Calendar next = Calendar.getInstance();
            next.set(Calendar.HOUR_OF_DAY, hour);
            next.set(Calendar.MINUTE, minute);
            next.set(Calendar.SECOND, 0);
            next.set(Calendar.MILLISECOND, 0);

            if (!next.after(now)) {
                next.add(Calendar.DAY_OF_MONTH, 1);
            }

            long delayTicks = Math.max(20L, (next.getTimeInMillis() - now.getTimeInMillis()) / 50L);
            long periodTicks = 24L * 60 * 60 * 20;

            Bukkit.getScheduler().runTaskTimerAsynchronously(this, rotationTask, delayTicks, periodTicks);
            getLogger().info("Rotation scheduled for " + rotationTime + " (in " + (delayTicks / 20) + "s)");
        } catch (Exception e) {
            getLogger().warning("Failed to schedule rotation: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (contractManager != null) contractManager.shutdown();
        if (registry != null) registry.shutdown();
        if (playerContractDatabase != null) playerContractDatabase.close();
        if (npcQuestDatabase != null) npcQuestDatabase.close();
        if (database != null) database.close();
        getLogger().info("LoveContracts disabled");
    }

    private org.bukkit.configuration.file.FileConfiguration headsConfig;

    public void loadHeadsConfig() {
        java.io.File headsFile = new java.io.File(getDataFolder(), "heads.yml");
        if (!headsFile.exists()) {
            saveResource("heads.yml", false);
        }
        headsConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(headsFile);
    }

    public org.bukkit.configuration.file.FileConfiguration getHeadsConfig() {
        if (headsConfig == null) {
            loadHeadsConfig();
        }
        return headsConfig;
    }

    public static LoveContracts getInstance() { return instance; }
    public ContractDatabase getDatabase() { return database; }
    public ContractRegistry getRegistry() { return registry; }
    public ContractManager getContractManager() { return contractManager; }
    public me.lovelace.lovecontracts.manager.ContractSignManager getSignManager() { return signManager; }
    public RewardProcessor getRewardProcessor() { return rewardProcessor; }
    public SyncManager getSyncManager() { return syncManager; }
    public ContractGUI getContractGUI() { return contractGUI; }
    public ContractConfirmGUI getConfirmGUI() { return confirmGUI; }
    public me.lovelace.lovecontracts.gui.ContractCreateGUI getContractCreateGUI() { return contractCreateGUI; }
    public PlayerContractDatabase getPlayerContractDatabase() { return playerContractDatabase; }
    public PlayerContractManager getPlayerContractManager() { return playerContractManager; }
    public PlayerContractBoardGUI getPlayerContractBoardGUI() { return playerContractBoardGUI; }
    public PlayerContractMyGUI getPlayerContractMyGUI() { return playerContractMyGUI; }
    public me.lovelace.lovecontracts.npc.manager.NpcQuestManager getNpcQuestManager() { return npcQuestManager; }
    public me.lovelace.lovecontracts.npc.gui.NpcDialogueGUI getNpcDialogueGUI() { return npcDialogueGUI; }
    public me.lovelace.lovecontracts.manager.FineManager getFineManager() { return fineManager; }
    public MessageManager getMessageManager() { return messageManager; }
    public Optional<StatBus> getStatBus() { return statBus; }
}
