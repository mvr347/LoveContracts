package me.lovelace.lovecontracts.npc.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Variable reward tiers for unified quests: bonuses for completion speed,
 * polite NPC interactions, and quality (no deaths / surplus delivery).
 * All bonuses are bounded by the configured maximum multiplier.
 */
public record RewardTiers(
        double speedFastRatio,
        double speedFastBonus,
        double speedMediumRatio,
        double speedMediumBonus,
        double interactionBonus1,
        double interactionBonus2,
        double noDeathBonus,
        double surplusBonus,
        double maxMultiplier
) {

    public static final RewardTiers DEFAULT = new RewardTiers(
            0.40, 0.25,
            0.70, 0.10,
            0.10, 0.15,
            0.10, 0.05,
            1.50
    );

    public record Calculation(long finalReward, double multiplier, List<String> breakdown) {
    }

    public static RewardTiers parse(ConfigurationSection sec) {
        if (sec == null) return DEFAULT;
        return new RewardTiers(
                clampPositive(sec.getDouble("speed-fast-ratio", DEFAULT.speedFastRatio), DEFAULT.speedFastRatio),
                Math.max(0.0, sec.getDouble("speed-fast-bonus", DEFAULT.speedFastBonus)),
                clampPositive(sec.getDouble("speed-medium-ratio", DEFAULT.speedMediumRatio), DEFAULT.speedMediumRatio),
                Math.max(0.0, sec.getDouble("speed-medium-bonus", DEFAULT.speedMediumBonus)),
                Math.max(0.0, sec.getDouble("interaction-bonus-1", DEFAULT.interactionBonus1)),
                Math.max(0.0, sec.getDouble("interaction-bonus-2", DEFAULT.interactionBonus2)),
                Math.max(0.0, sec.getDouble("no-death-bonus", DEFAULT.noDeathBonus)),
                Math.max(0.0, sec.getDouble("surplus-bonus", DEFAULT.surplusBonus)),
                Math.max(1.0, sec.getDouble("max-multiplier", DEFAULT.maxMultiplier))
        );
    }

    private static double clampPositive(double v, double fallback) {
        return Double.isFinite(v) && v > 0 ? Math.min(1.0, v) : fallback;
    }

    public double effectiveMaxMultiplier(double globalMaxMultiplier) {
        double local = maxMultiplier >= 1.0 ? maxMultiplier : 1.0;
        double global = globalMaxMultiplier >= 1.0 ? globalMaxMultiplier : local;
        return Math.max(1.0, Math.min(local, global));
    }

    public long maxReward(long baseReward, double globalMaxMultiplier) {
        if (baseReward <= 0) return 0;
        return Math.round(baseReward * effectiveMaxMultiplier(globalMaxMultiplier));
    }

    public Calculation calculate(
            long baseReward,
            long elapsedMillis,
            int timeLimitMinutes,
            int bonusInteractions,
            boolean noDeaths,
            int surplusDelivered,
            double globalMaxMultiplier
    ) {
        if (baseReward <= 0) return new Calculation(0, 1.0, List.of());

        double bonus = 0.0;
        List<String> breakdown = new ArrayList<>();

        if (timeLimitMinutes > 0 && elapsedMillis >= 0) {
            long limitMillis = timeLimitMinutes * 60_000L;
            double ratio = (double) elapsedMillis / limitMillis;
            if (ratio <= speedFastRatio && speedFastBonus > 0) {
                bonus += speedFastBonus;
                breakdown.add("Скорость +" + Math.round(speedFastBonus * 100) + "%");
            } else if (ratio <= speedMediumRatio && speedMediumBonus > 0) {
                bonus += speedMediumBonus;
                breakdown.add("Скорость +" + Math.round(speedMediumBonus * 100) + "%");
            }
        }

        if (bonusInteractions >= 2 && interactionBonus2 > 0) {
            bonus += interactionBonus2;
            breakdown.add("Вежливость +" + Math.round(interactionBonus2 * 100) + "%");
        } else if (bonusInteractions == 1 && interactionBonus1 > 0) {
            bonus += interactionBonus1;
            breakdown.add("Вежливость +" + Math.round(interactionBonus1 * 100) + "%");
        }

        double quality = 0.0;
        if (noDeaths && noDeathBonus > 0) quality += noDeathBonus;
        if (surplusDelivered > 0 && surplusBonus > 0) quality += surplusBonus;
        if (quality > 0) {
            bonus += quality;
            breakdown.add("Качество +" + Math.round(quality * 100) + "%");
        }

        double cap = effectiveMaxMultiplier(globalMaxMultiplier);
        double mult = Math.min(cap, 1.0 + bonus);
        long finalReward = Math.round(baseReward * mult);
        return new Calculation(finalReward, mult, List.copyOf(breakdown));
    }
}
