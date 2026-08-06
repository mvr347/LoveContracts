package me.lovelace.lovecontracts.player.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Контракт "игрок нанимает игрока". Награда (золото, физическая валюта LoveCore) удерживается
 * в эскроу LoveContracts с момента создания — физически списывается у создателя сразу, чтобы
 * исполнитель гарантированно получил оплату независимо от того, останется ли у создателя
 * достаточно денег к моменту завершения.
 *
 * <p>Мутабельный объект — состояние (status, executor, progress) меняется по ходу жизни
 * контракта и каждый раз перезаписывается в БД менеджером.</p>
 */
public class PlayerContract {

    private final UUID id;
    private final UUID creatorId;
    private final String creatorName;
    private UUID executorId;
    private String executorName;

    private final String description;
    private final PlayerContractObjectiveType objectiveType;
    private final String objectiveTarget;
    private final int objectiveAmount;
    private int objectiveProgress;

    private final long goldReward;
    private final int reputationHint;

    private final PlayerContractVisibility visibility;
    private PlayerContractStatus status;

    private final Instant createdAt;
    private final Instant deadline;
    private Instant acceptedAt;
    private Instant completedAt;

    public PlayerContract(UUID id, UUID creatorId, String creatorName, UUID executorId, String executorName,
                           String description, PlayerContractObjectiveType objectiveType, String objectiveTarget,
                           int objectiveAmount, int objectiveProgress, long goldReward,
                           int reputationHint, PlayerContractVisibility visibility, PlayerContractStatus status,
                           Instant createdAt, Instant deadline, Instant acceptedAt, Instant completedAt) {
        this.id = Objects.requireNonNull(id);
        this.creatorId = Objects.requireNonNull(creatorId);
        this.creatorName = creatorName;
        this.executorId = executorId;
        this.executorName = executorName;
        this.description = description;
        this.objectiveType = Objects.requireNonNull(objectiveType);
        this.objectiveTarget = objectiveTarget;
        this.objectiveAmount = Math.max(1, objectiveAmount);
        this.objectiveProgress = objectiveProgress;
        this.goldReward = Math.max(0, goldReward);
        this.reputationHint = reputationHint;
        this.visibility = visibility != null ? visibility : PlayerContractVisibility.PUBLIC;
        this.status = status != null ? status : PlayerContractStatus.OPEN;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.deadline = Objects.requireNonNull(deadline);
        this.acceptedAt = acceptedAt;
        this.completedAt = completedAt;
    }

    public UUID getId() { return id; }
    public UUID getCreatorId() { return creatorId; }
    public String getCreatorName() { return creatorName; }
    public UUID getExecutorId() { return executorId; }
    public String getExecutorName() { return executorName; }
    public String getDescription() { return description; }
    public PlayerContractObjectiveType getObjectiveType() { return objectiveType; }
    public String getObjectiveTarget() { return objectiveTarget; }
    public int getObjectiveAmount() { return objectiveAmount; }
    public int getObjectiveProgress() { return objectiveProgress; }
    public long getGoldReward() { return goldReward; }
    public int getReputationHint() { return reputationHint; }
    public PlayerContractVisibility getVisibility() { return visibility; }
    public PlayerContractStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeadline() { return deadline; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void setExecutor(UUID executorId, String executorName) {
        this.executorId = executorId;
        this.executorName = executorName;
    }

    public void clearExecutor() {
        this.executorId = null;
        this.executorName = null;
    }

    public void setStatus(PlayerContractStatus status) { this.status = status; }
    public void setObjectiveProgress(int progress) { this.objectiveProgress = Math.max(0, progress); }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public boolean isExpired() {
        return Instant.now().isAfter(deadline);
    }

    public boolean isObjectiveMet() {
        return objectiveProgress >= objectiveAmount;
    }

    public String getProgressString() {
        return objectiveProgress + "/" + objectiveAmount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerContract that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return "PlayerContract{id=" + id + ", status=" + status + ", type=" + objectiveType + "}";
    }
}
