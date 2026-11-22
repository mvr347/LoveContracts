package me.lovelace.lovecontracts.npc.reward;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reward bonuses on top of the base reward: for speed (finished well inside the time limit), for dealing politely with
 * the NPCs and for quality (no deaths, delivered with a surplus). The total multiplier is clamped by
 * {@code maxMultiplier} so bonuses never blow past the rolling income limit's intent.
 */
public record RewardTiers(List<Speed> speed, double interactionBonus, double maxInteractionBonus,
                          double noDeathsBonus, double surplusBonus, double maxMultiplier) {

    /** A finish within {@code within} (share of the time limit, 0..1) earns {@code bonus} (0.25 = +25%). */
    public record Speed(double within, double bonus) {
    }

    /** What a finished quest earned, for the completion message. Bonuses are fractions (0.25 = +25%). */
    public record Breakdown(long total, double speed, double interactions, double quality) {
    }

    public RewardTiers {
        speed = speed.stream().sorted(Comparator.comparingDouble(Speed::within)).toList();
        maxMultiplier = Math.max(1.0, maxMultiplier);
    }

    /** No bonuses at all: what a quest without a declared tier pays, exactly its base reward. */
    public static RewardTiers none() {
        return new RewardTiers(List.of(), 0, 0, 0, 0, 1.0);
    }

    public static RewardTiers defaults() {
        return new RewardTiers(List.of(new Speed(0.4, 0.25), new Speed(0.7, 0.10)),
                0.05, 0.15, 0.05, 0.0, 1.5);
    }

    /** Reads the {@code reward-tiers} section; every missing key falls back to {@code base}. */
    public static RewardTiers read(ConfigurationSection section, RewardTiers base) {
        if (section == null) return base;
        List<Speed> speeds = new ArrayList<>();
        List<?> list = section.getList("speed");
        if (list != null) {
            for (Object entry : list) {
                if (entry instanceof java.util.Map<?, ?> map && map.get("within") instanceof Number within
                        && map.get("bonus") instanceof Number bonus) {
                    speeds.add(new Speed(within.doubleValue(), bonus.doubleValue()));
                }
            }
        }
        return new RewardTiers(speeds.isEmpty() ? base.speed : speeds,
                section.getDouble("interaction-bonus", base.interactionBonus),
                section.getDouble("max-interaction-bonus", base.maxInteractionBonus),
                section.getDouble("no-deaths-bonus", base.noDeathsBonus),
                section.getDouble("surplus-bonus", base.surplusBonus),
                section.getDouble("max-multiplier", base.maxMultiplier));
    }

    /**
     * @param base          base reward, already capped by {@code economy.contracts-v2.max-reward}
     * @param limitMillis   time limit of the quest, 0 when it has none (no speed bonus then)
     * @param elapsedMillis time from taking the quest to finishing it
     * @param politeChoices how many marked polite/friendly dialogue answers the player picked
     * @param noDeaths      the player did not die while the quest was active
     * @param surplus       the player delivered more than required
     */
    public Breakdown compute(long base, long limitMillis, long elapsedMillis, int politeChoices,
                             boolean noDeaths, boolean surplus) {
        double speedBonus = 0;
        if (limitMillis > 0 && elapsedMillis >= 0) {
            double share = (double) elapsedMillis / limitMillis;
            for (Speed tier : speed) {
                if (share <= tier.within) {
                    speedBonus = tier.bonus;
                    break;
                }
            }
        }
        double interactions = Math.min(maxInteractionBonus, Math.max(0, politeChoices) * interactionBonus);
        double quality = (noDeaths ? noDeathsBonus : 0) + (surplus ? surplusBonus : 0);
        double multiplier = Math.min(maxMultiplier, 1.0 + speedBonus + interactions + quality);
        return new Breakdown(Math.round(base * multiplier), speedBonus, interactions, quality);
    }

    /** The most a quest can pay: shown on the card as "up to N". */
    public long maximum(long base, boolean timed) {
        double best = timed && !speed.isEmpty() ? speed.stream().mapToDouble(Speed::bonus).max().orElse(0) : 0;
        double multiplier = Math.min(maxMultiplier, 1.0 + best + maxInteractionBonus + noDeathsBonus + surplusBonus);
        return Math.round(base * multiplier);
    }
}
