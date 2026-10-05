package me.lovelace.lovecontracts.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerContractCommandTest {

    @Test
    void plainNumberIsCopper() {
        assertEquals(150L, PlayerContractCommand.parseReward("150"));
        assertEquals(1L, PlayerContractCommand.parseReward("1"));
    }
}
