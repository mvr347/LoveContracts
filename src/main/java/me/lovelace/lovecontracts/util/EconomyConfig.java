package me.lovelace.lovecontracts.util;

import dev.lovelace.lovecore.api.economy.MoneyConfig;
import me.lovelace.lovecontracts.LoveContracts;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * Единая точка чтения денежных ключей плагина: значение может быть числом или строкой вида
 * {@code "3i 50c"}; поверх него накладываются {@code economy.reward-scale} / {@code economy.penalty-scale}
 * и (если включено) общий индекс цен LoveCore.
 */
public final class EconomyConfig {

    public static final String REWARD_SCALE = "economy.reward-scale";
    public static final String PENALTY_SCALE = "economy.penalty-scale";

    private EconomyConfig() {
    }

    public static double scale(LoveContracts plugin, String key) {
        double s = plugin.getConfig().getDouble(key, 1.0);
        return s > 0 && Double.isFinite(s) ? s : 1.0;
    }

    /** Сумма из секции контракта/квеста: ключ × масштаб плагина × индекс цен. 0, если ключа нет. */
    public static double read(LoveContracts plugin, ConfigurationSection section, String path, String scaleKey) {
        if (section == null || !section.contains(path)) return 0.0;
        long base = MoneyConfig.get(section, path, 0L);
        if (base <= 0) return 0.0;
        long scaled = Math.max(1L, Math.round(base * scale(plugin, scaleKey)));
        if (plugin.getConfig().getBoolean("economy.use-price-index", true)) {
            final long before = scaled;
            scaled = CoinFormat.tryEconomy().map(e -> e.scaled(before)).orElse(before);
        }
        return scaled;
    }

    /** Шаги суммы награды в меню создания контракта (клик по кругу). */
    public static List<Long> rewardSteps(LoveContracts plugin) {
        List<Long> steps = new java.util.ArrayList<>();
        List<?> raw = plugin.getConfig().getList("economy.creation-reward-steps");
        if (raw != null) {
            for (Object o : raw) {
                long v;
                if (o instanceof Number n) {
                    v = n.longValue();
                } else {
                    try {
                        v = CoinFormat.tryEconomy().map(e -> e.parse(String.valueOf(o)))
                                .orElseGet(() -> Long.parseLong(String.valueOf(o)));
                    } catch (RuntimeException ex) {
                        plugin.getLogger().warning("economy.creation-reward-steps: не разобрано значение '" + o + "'");
                        continue;
                    }
                }
                if (v > 0) steps.add(v);
            }
        }
        if (steps.isEmpty()) steps = List.of(100L, 500L, 2000L, 10000L);
        return steps;
    }
}
