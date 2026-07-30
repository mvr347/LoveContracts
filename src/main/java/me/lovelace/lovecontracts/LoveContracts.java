package me.lovelace.lovecontracts;

import dev.lovelace.lovecore.api.stats.StatBus;
import me.lovelace.lovecontracts.command.ContractCommand;
import me.lovelace.lovecontracts.command.LoveContractsAdminCommand;
import me.lovelace.lovecontracts.gui.ContractGUI;
import me.lovelace.lovecontracts.gui.ContractStatsGUI;
import me.lovelace.lovecontracts.integration.CitizensIntegration;
import me.lovelace.lovecontracts.listener.ContractNpcListener;
import me.lovelace.lovecontracts.listener.ContractSignListener;
import me.lovelace.lovecontracts.manager.ContractManager;
import me.lovelace.lovecontracts.manager.ContractRegistry;
import me.lovelace.lovecontracts.manager.RewardProcessor;
import me.lovelace.lovecontracts.manager.SyncManager;
import me.lovelace.lovecontracts.service.ContractPlaceholderExpansion;
import me.lovelace.lovecontracts.storage.ContractDatabase;
import me.lovelace.lovecontracts.task.ContractExpirationTask;
import me.lovelace.lovecontracts.task.ContractRotationTask;
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
    private RewardProcessor rewardProcessor;
    private SyncManager syncManager;
    private ContractGUI contractGUI;
    private ContractStatsGUI statsGUI;
    private ContractRotationTask rotationTask;
    private ContractExpirationTask expirationTask;
    private Optional<StatBus> statBus = Optional.empty();

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResource("contracts.yml", false);
        saveResource("messages.yml", false);

        try {
            database = new ContractDatabase(this);
            database.initialize();
        } catch (Exception e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        registry = new ContractRegistry(this);
        registry.loadFromConfig();

        statBus = Optional.ofNullable(Bukkit.getServicesManager().load(StatBus.class));
        if (statBus.isPresent()) {
            getLogger().info("LoveCore StatBus hooked for metric reporting");
        }

        rewardProcessor = new RewardProcessor(this);
        syncManager = new SyncManager(this);
        contractManager = new ContractManager(this);
        contractGUI = new ContractGUI(this);
        statsGUI = new ContractStatsGUI(this);

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

        Bukkit.getPluginManager().registerEvents(new ContractSignListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ContractNpcListener(this, new CitizensIntegration()), this);
        Bukkit.getPluginManager().registerEvents(contractGUI, this);
        Bukkit.getPluginManager().registerEvents(statsGUI, this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ContractPlaceholderExpansion(this).register();
            getLogger().info("PlaceholderAPI hooked");
        }

        rotationTask = new ContractRotationTask(this);
        scheduleRotationTask();
        expirationTask = new ContractExpirationTask(this);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, expirationTask, 20L * 60, 20L * 60);

        contractManager.startRotationTask();

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
        if (database != null) database.close();
        getLogger().info("LoveContracts disabled");
    }

    public static LoveContracts getInstance() { return instance; }
    public ContractDatabase getDatabase() { return database; }
    public ContractRegistry getRegistry() { return registry; }
    public ContractManager getContractManager() { return contractManager; }
    public RewardProcessor getRewardProcessor() { return rewardProcessor; }
    public SyncManager getSyncManager() { return syncManager; }
    public ContractGUI getContractGUI() { return contractGUI; }
    public ContractStatsGUI getStatsGUI() { return statsGUI; }
    public Optional<StatBus> getStatBus() { return statBus; }
}
