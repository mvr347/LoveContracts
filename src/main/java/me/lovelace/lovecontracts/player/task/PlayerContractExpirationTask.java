package me.lovelace.lovecontracts.player.task;

import me.lovelace.lovecontracts.player.manager.PlayerContractManager;

/** Раз в тик-интервал проверяет просроченные player-контракты и закрывает их с рефандом. */
public class PlayerContractExpirationTask implements Runnable {

    private final PlayerContractManager manager;

    public PlayerContractExpirationTask(PlayerContractManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        manager.expireOverdue();
    }
}
