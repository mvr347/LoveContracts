package me.lovelace.lovecontracts.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/**
 * Мост к глашатаю LoveTweaks: смену дневных контрактов объявляет он, а не строка в общем чате.
 * LoveTweaks не регистрирует сервис через ServicesManager, поэтому рефлексия идёт прямо на
 * экземпляр плагина: LoveTweaks#getHeraldManager() -> HeraldManager#announceContractsRotation().
 * Если LoveTweaks не установлен, отключён или собран без этого метода — объявления просто нет,
 * ротация от этого не страдает.
 */
public final class HeraldBridge {

    private final JavaPlugin plugin;

    public HeraldBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Вызывать с главного потока: HeraldManager рассылает через Bukkit broadcast. */
    public void announceContractsRotation() {
        Plugin loveTweaks = Bukkit.getPluginManager().getPlugin("LoveTweaks");
        if (loveTweaks == null || !loveTweaks.isEnabled()) {
            return;
        }
        try {
            Method getHeraldManager = loveTweaks.getClass().getMethod("getHeraldManager");
            Object heraldManager = getHeraldManager.invoke(loveTweaks);
            if (heraldManager == null) {
                return;
            }
            heraldManager.getClass().getMethod("announceContractsRotation").invoke(heraldManager);
        } catch (NoSuchMethodException ignored) {
            // Установлена более старая версия LoveTweaks без этого объявления.
        } catch (Exception e) {
            plugin.getLogger().fine("Не удалось объявить смену контрактов через LoveTweaks: " + e.getMessage());
        }
    }
}
