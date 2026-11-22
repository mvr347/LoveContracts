package me.lovelace.lovecontracts.npc.model;

/**
 * Anti-farm rule for cancelling taken quests (so nobody hoards quests to keep them from others): after
 * {@code threshold} cancels in a row the contractor refuses to work with the player for a while. The block grows
 * with each repeat and the level decays after a quiet period, so one mistake does not mark a player forever.
 */
public final class RefusalPolicy {

    /** Stored state of one player. {@code lockedUntil} is epoch millis, 0 when never locked. */
    public record State(int streak, int lockLevel, long lockedUntil, long lastLockAt, int totalLocks) {
        public static final State EMPTY = new State(0, 0, 0, 0, 0);
    }

    /** Settings: consecutive cancels that trigger a block, block lengths per level, days until the level decays. */
    public record Config(int threshold, long[] lockMillis, int levelDecayDays) {
        public Config {
            if (threshold < 1) threshold = 3;
            if (lockMillis == null || lockMillis.length == 0) lockMillis = new long[] {3_600_000L, 86_400_000L, 604_800_000L};
        }

        public static Config defaults() {
            return new Config(3, new long[] {3_600_000L, 86_400_000L, 604_800_000L}, 30);
        }
    }

    private RefusalPolicy() {
    }

    public static boolean locked(State state, long now) {
        return state.lockedUntil() > now;
    }

    public static long remaining(State state, long now) {
        return Math.max(0, state.lockedUntil() - now);
    }

    /** The player cancelled a taken quest. A block starts when the streak reaches the threshold. */
    public static State onCancel(State state, long now, Config config) {
        State current = decayed(state, now, config);
        int streak = current.streak() + 1;
        if (streak < config.threshold()) {
            return new State(streak, current.lockLevel(), current.lockedUntil(), current.lastLockAt(), current.totalLocks());
        }
        int level = current.lockLevel();
        long length = config.lockMillis()[Math.min(level, config.lockMillis().length - 1)];
        // the streak restarts after a block: three more cancels are needed for the next one
        return new State(0, level + 1, now + length, now, current.totalLocks() + 1);
    }

    /** The player finished a quest: the streak of cancels is over. The block level only decays with time. */
    public static State onCompleted(State state) {
        return new State(0, state.lockLevel(), state.lockedUntil(), state.lastLockAt(), state.totalLocks());
    }

    /** Drops the escalation level after {@code levelDecayDays} without a block. */
    public static State decayed(State state, long now, Config config) {
        if (state.lockLevel() <= 0 || config.levelDecayDays() <= 0 || state.lastLockAt() <= 0) return state;
        long quiet = now - Math.max(state.lastLockAt(), state.lockedUntil());
        if (quiet < config.levelDecayDays() * 86_400_000L) return state;
        return new State(state.streak(), 0, state.lockedUntil(), state.lastLockAt(), state.totalLocks());
    }
}
