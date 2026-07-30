package me.lovelace.lovecontracts.player.model;

public enum PlayerContractVisibility {
    /** Виден всем на доске контрактов. */
    PUBLIC,
    /** Виден только сокланам создателя (требует LoveCore + ProfileOracle). */
    CLAN_ONLY
}
