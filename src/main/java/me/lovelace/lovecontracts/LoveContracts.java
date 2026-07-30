package me.lovelace.lovecontracts;

import me.lovelace.lovecontracts.command.ContractCommand;
import me.lovelace.lovecontracts.command.LoveContractsAdminCommand;
import me.lovelace.lovecontracts.gui.ContractGUI;
import me.lovelace.lovecontracts.listener.ContractSignListener;
import me.lovelace.lovecontracts.manager.ContractManager;
import me.lovelace.lovecontracts.manager.ContractRegistry;
import me.lovelace.lovecontracts.manager.RewardProcessor;
import me.lovelace.lovecontracts.manager.SyncManager;
import me.lovelace.lovecontracts.service.ContractPlaceholderExpansion;
import me.lovelace.lovecontracts.storage.ContractDatabase;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class LoveContracts extends JavaPlugin {

    private static LoveContracts instance;

    private ContractDatabase database;
    private ContractRegistry registry;
    private ContractManager contractManager;
    private RewardProcessor rewardProcessor;
    private SyncManager syncManager;
    private ContractGUI contractGUI;

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

        rewardProcessor = new RewardProcessor(this);
        syncManager = new SyncManager(this);
        contractManager = new ContractManager(this);
        contractGUI = new ContractGUI(this);

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
        Bukkit.getPluginManager().registerEvents(contractGUI, this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ContractPlaceholderExpansion(this).register();
            getLogger().info("PlaceholderAPI hooked");
        }

        contractManager.startRotationTask();

        getLogger().info("LoveContracts v" + getPluginMeta().getVersion() + " enabled — "
                + registry.size() + " contracts loaded");
    }

    @Override
    public void onDisable() {
        if (contractManager != null) {
            contractManager.shutdown();
        }
        if (registry != null) {
            registry.shutdown();
        }
        if (database != null) {
            database.close();
        }
        getLogger().info("LoveContracts disabled");
    }

    public static LoveContracts getInstance() {
        return instance;
    }

    public ContractDatabase getDatabase() {
        return database;
    }

    public ContractRegistry getRegistry() {
        return registry;
    }

    public ContractManager getContractManager() {
        return contractManager;
    }

    public RewardProcessor getRewardProcessor() {
        return rewardProcessor;
    }

    public SyncManager getSyncManager() {
        return syncManager;
    }

    public ContractGUI getContractGUI() {
        return contractGUI;
    }
}
