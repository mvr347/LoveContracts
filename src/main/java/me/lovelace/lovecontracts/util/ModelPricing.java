package me.lovelace.lovecontracts.util;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.PriceOracle;
import me.lovelace.lovecontracts.LoveContracts;
import org.bukkit.Material;

import java.util.OptionalLong;
import java.util.function.DoubleSupplier;

/**
 * Links item-based contract rewards to LoveCore's price model (economy v2).
 *
 * <p>The reward written in contracts.yml was balanced by hand; the model says what the goods are worth
 * now. The reward is therefore multiplied by {@code clamp(model / yml)}: it follows price changes but a
 * wrong model price cannot make a reward absurd. The ratio is evaluated on every read, so it starts
 * working as soon as the model is built (LoveCore builds it after this plugin has already loaded).</p>
 */
public final class ModelPricing {

    private ModelPricing() {
    }

    /** Price of one item from the model; ores are priced by what they drop. */
    public static OptionalLong unitValue(Material material) {
        if (material == null) return OptionalLong.empty();
        PriceOracle oracle;
        try {
            oracle = LoveCore.service(PriceOracle.class).orElse(null);
        } catch (Throwable t) {
            return OptionalLong.empty();
        }
        if (oracle == null || !oracle.ready()) return OptionalLong.empty();
        OptionalLong direct = oracle.value(material);
        if (direct.isPresent() && direct.getAsLong() > 0) return direct;
        Material drop = oreDrop(material);
        if (drop != null) {
            OptionalLong dropValue = oracle.value(drop);
            if (dropValue.isPresent() && dropValue.getAsLong() > 0) return dropValue;
        }
        return OptionalLong.empty();
    }

    /** What an ore block gives when mined (the model prices items, not blocks); null if it is not an ore. */
    static Material oreDrop(Material ore) {
        String name = ore.name();
        if (!name.endsWith("_ORE")) return null;
        String base = name.substring(0, name.length() - "_ORE".length());
        if (base.startsWith("DEEPSLATE_")) base = base.substring("DEEPSLATE_".length());
        return switch (base) {
            case "IRON", "GOLD", "COPPER" -> Material.matchMaterial("RAW_" + base);
            case "NETHER_GOLD" -> Material.GOLD_NUGGET;
            case "NETHER_QUARTZ" -> Material.QUARTZ;
            default -> Material.matchMaterial(base);
        };
    }

    /**
     * Multiplier for a contract's reward and penalty: {@code model price × count × work-factor / yml reward},
     * limited to [min-ratio, max-ratio]. 1.0 while the model is off, not ready or does not know the item.
     */
    public static DoubleSupplier ratio(LoveContracts plugin, String materialName, long count, double ymlReward) {
        if (materialName == null || count <= 0 || ymlReward <= 0) return () -> 1.0;
        Material material = Material.matchMaterial(materialName);
        if (material == null) return () -> 1.0;
        return () -> {
            if (!plugin.getConfig().getBoolean("economy.model-rewards.enabled", true)) return 1.0;
            OptionalLong unit = unitValue(material);
            if (unit.isEmpty()) return 1.0;
            double factor = plugin.getConfig().getDouble("economy.model-rewards.work-factor", 0.5);
            double min = plugin.getConfig().getDouble("economy.model-rewards.min-ratio", 0.5);
            double max = plugin.getConfig().getDouble("economy.model-rewards.max-ratio", 3.0);
            return clamp(unit.getAsLong() * (double) count * factor / ymlReward, min, max);
        };
    }

    static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return 1.0;
        return Math.max(min, Math.min(max, value));
    }
}
