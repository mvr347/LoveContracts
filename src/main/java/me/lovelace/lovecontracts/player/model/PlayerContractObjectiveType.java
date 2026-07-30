package me.lovelace.lovecontracts.player.model;

/**
 * Тип задачи контракта. Каждый тип самодостаточен — не зависит от других плагинов,
 * прогресс отслеживается собственными листенерами LoveContracts.
 */
public enum PlayerContractObjectiveType {
    /** Принести N предметов материала {@code objectiveTarget}. Проверяется на /pcontract turnin. */
    DELIVER_ITEM,
    /** Убить N существ типа {@code objectiveTarget}. Прогресс отслеживается через EntityDeathEvent. */
    KILL_ENTITY,
    /** Произвольная задача текстом — исполнитель сдаёт на ревью, наниматель подтверждает/отклоняет. */
    CUSTOM
}
