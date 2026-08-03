package me.lovelace.lovecontracts.model;

import me.lovelace.lovecontracts.condition.ContractCondition;
import java.util.*;

public class Contract {

    private final String id;
    private final String displayName;
    private final String description;
    private final Difficulty difficulty;
    private final ContractType type;
    private final int maxAcceptances;
    private final int dailySpawns;
    private final int weight;
    private final int expirationMinutes;
    private final boolean starter;
    private final boolean enabled;
    private ContractCondition condition;
    private final List<Reward> rewards;
    private final List<Penalty> penalties;
    private final ContractRequirement requirement;

    public Contract(String id, String displayName, String description, Difficulty difficulty,
                    ContractType type, int maxAcceptances, int dailySpawns, int weight,
                    int expirationMinutes, boolean starter, boolean enabled, List<Reward> rewards, List<Penalty> penalties) {
        this(id, displayName, description, difficulty, type, maxAcceptances, dailySpawns, weight, expirationMinutes, starter, enabled, rewards, penalties, null);
    }

    public Contract(String id, String displayName, String description, Difficulty difficulty,
                    ContractType type, int maxAcceptances, int dailySpawns, int weight,
                    int expirationMinutes, boolean starter, boolean enabled, List<Reward> rewards, List<Penalty> penalties,
                    ContractRequirement requirement) {
        this.id = Objects.requireNonNull(id);
        this.displayName = displayName != null ? displayName : id;
        this.description = description != null ? description : "";
        this.difficulty = difficulty != null ? difficulty : Difficulty.EASY;
        this.type = type != null ? type : ContractType.REPEATING;
        this.maxAcceptances = maxAcceptances;
        this.dailySpawns = Math.max(1, dailySpawns);
        this.weight = weight > 0 ? weight : this.difficulty.getDefaultWeight();
        this.expirationMinutes = expirationMinutes > 0 ? expirationMinutes : 1440;
        this.starter = starter || difficulty == Difficulty.STARTER;
        this.enabled = enabled;
        this.rewards = rewards != null ? new ArrayList<>(rewards) : new ArrayList<>();
        this.penalties = penalties != null ? new ArrayList<>(penalties) : new ArrayList<>();
        this.requirement = requirement;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public Difficulty getDifficulty() { return difficulty; }
    public ContractType getType() { return type; }
    public int getMaxAcceptances() { return maxAcceptances; }
    public int getDailySpawns() { return dailySpawns; }
    public int getWeight() { return weight; }
    public int getExpirationMinutes() { return expirationMinutes; }
    public int getExpirationHours() { return (int) Math.ceil(expirationMinutes / 60.0); }
    public boolean isStarter() { return starter; }
    public boolean isEnabled() { return enabled; }
    public ContractCondition getCondition() { return condition; }
    public void setCondition(ContractCondition condition) { this.condition = condition; }
    public List<Reward> getRewards() { return Collections.unmodifiableList(rewards); }
    public List<Penalty> getPenalties() { return Collections.unmodifiableList(penalties); }
    public ContractRequirement getRequirement() { return requirement; }
    public boolean isRepeating() { return type == ContractType.REPEATING; }
    public boolean isOneTime() { return type == ContractType.ONE_TIME; }

    public String getFormattedDuration() {
        if (expirationMinutes < 60) {
            return expirationMinutes + " мин";
        }
        int hours = expirationMinutes / 60;
        int mins = expirationMinutes % 60;
        if (mins == 0) {
            return hours + " ч";
        }
        return hours + " ч " + mins + " мин";
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Contract c)) return false;
        return id.equals(c.id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "Contract{id='" + id + "', difficulty=" + difficulty + ", starter=" + starter + "}";
    }
}
