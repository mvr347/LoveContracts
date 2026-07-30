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
    private final int expirationHours;
    private final boolean enabled;
    private ContractCondition condition;
    private final List<Reward> rewards;
    private final List<Penalty> penalties;

    public Contract(String id, String displayName, String description, Difficulty difficulty,
                    ContractType type, int maxAcceptances, int dailySpawns, int weight,
                    int expirationHours, boolean enabled, List<Reward> rewards, List<Penalty> penalties) {
        this.id = Objects.requireNonNull(id);
        this.displayName = displayName != null ? displayName : id;
        this.description = description != null ? description : "";
        this.difficulty = difficulty != null ? difficulty : Difficulty.EASY;
        this.type = type != null ? type : ContractType.REPEATING;
        this.maxAcceptances = maxAcceptances;
        this.dailySpawns = Math.max(1, dailySpawns);
        this.weight = weight > 0 ? weight : this.difficulty.getDefaultWeight();
        this.expirationHours = expirationHours > 0 ? expirationHours : 24;
        this.enabled = enabled;
        this.rewards = rewards != null ? new ArrayList<>(rewards) : new ArrayList<>();
        this.penalties = penalties != null ? new ArrayList<>(penalties) : new ArrayList<>();
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public Difficulty getDifficulty() { return difficulty; }
    public ContractType getType() { return type; }
    public int getMaxAcceptances() { return maxAcceptances; }
    public int getDailySpawns() { return dailySpawns; }
    public int getWeight() { return weight; }
    public int getExpirationHours() { return expirationHours; }
    public boolean isEnabled() { return enabled; }
    public ContractCondition getCondition() { return condition; }
    public void setCondition(ContractCondition condition) { this.condition = condition; }
    public List<Reward> getRewards() { return Collections.unmodifiableList(rewards); }
    public List<Penalty> getPenalties() { return Collections.unmodifiableList(penalties); }
    public boolean isRepeating() { return type == ContractType.REPEATING; }
    public boolean isOneTime() { return type == ContractType.ONE_TIME; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Contract c)) return false;
        return id.equals(c.id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "Contract{id='" + id + "', difficulty=" + difficulty + ", type=" + type + "}";
    }
}
