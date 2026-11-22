package me.lovelace.lovecontracts.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChainAdminCommandTest {

    private static ChainAdminCommand.Parsed parse(String... args) {
        String[] all = new String[args.length + 1];
        all[0] = "chain";
        System.arraycopy(args, 0, all, 1, args.length);
        return ChainAdminCommand.parse(all);
    }

    @Test
    void startFailAndClearTakeAPlayerAndAChain() {
        var start = parse("start", "Steve", "smith");
        assertTrue(start.ok());
        assertEquals("start", start.op());
        assertEquals("Steve", start.player());
        assertEquals("smith", start.chain());
        assertTrue(parse("fail", "Steve", "smith").ok());
        assertTrue(parse("clear", "Steve", "all").ok());
        assertEquals("all", parse("clear", "Steve", "all").chain());
    }

    @Test
    void operationNamesAreCaseInsensitive() {
        assertEquals("start", parse("START", "Steve", "smith").op());
    }

    @Test
    void infoNeedsOnlyAPlayer() {
        var info = parse("info", "Steve");
        assertTrue(info.ok());
        assertNull(info.chain());
    }

    @Test
    void missingPiecesAreExplainedNotThrown() {
        assertFalse(ChainAdminCommand.parse(new String[]{"chain"}).ok());
        assertFalse(parse("start").ok());
        assertFalse(parse("start", "Steve").ok());
        assertTrue(parse("start", "Steve").error().contains("цепочку"));
        assertFalse(parse("teleport", "Steve", "smith").ok());
        assertTrue(parse("teleport", "Steve", "smith").error().contains("Неизвестная"));
    }

    @Test
    void stepNeedsAPositiveWholeNumber() {
        var ok = parse("step", "Steve", "smith", "3");
        assertTrue(ok.ok());
        assertEquals(3, ok.step());
        assertFalse(parse("step", "Steve", "smith").ok());
        assertFalse(parse("step", "Steve", "smith", "two").ok());
        assertFalse(parse("step", "Steve", "smith", "0").ok());
        assertFalse(parse("step", "Steve", "smith", "-1").ok());
    }

    @Test
    void completeRewardsUnlessToldNot() {
        assertTrue(parse("complete", "Steve", "smith").reward());
        assertFalse(parse("complete", "Steve", "smith", "--no-reward").reward());
        assertFalse(parse("complete", "Steve", "smith", "--reward").ok());
    }

    @Test
    void extraArgumentsAreRejected() {
        assertFalse(parse("start", "Steve", "smith", "extra").ok());
        assertFalse(parse("fail", "Steve", "smith", "extra").ok());
    }
}
