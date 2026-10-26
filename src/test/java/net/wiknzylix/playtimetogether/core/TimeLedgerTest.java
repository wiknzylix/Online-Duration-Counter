package net.wiknzylix.playtimetogether.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class TimeLedgerTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final AtomicLong clock = new AtomicLong();
    private final TimeLedger ledger = new TimeLedger(clock::get);

    @Test
    void historicalSeedIsImportedOnlyOnce() {
        ledger.seed(ALICE, "Alice", 120_000L);
        ledger.seed(ALICE, "OldName", 900_000L);

        assertTrue(ledger.contains(ALICE));
        assertFalse(ledger.contains(BOB));
        assertEquals(new TimeLedger.Row(ALICE, "Alice", 120_000L, false), alice());
    }

    @Test
    void repeatedSnapshotsDoNotDoubleCountOrMutateEarlierSnapshots() {
        ledger.join(ALICE, "Alice");
        advanceMillis(2_000L);
        List<TimeLedger.Row> first = ledger.snapshot();
        assertEquals(2_000L, alice().totalMillis());
        assertEquals(2_000L, alice().totalMillis());

        advanceMillis(3_000L);
        assertEquals(5_000L, alice().totalMillis());
        assertEquals(2_000L, first.getFirst().totalMillis());
        assertThrows(UnsupportedOperationException.class, () -> first.clear());
    }

    @Test
    void duplicateJoinUpdatesNameWithoutResettingSession() {
        ledger.join(ALICE, "Alice");
        advanceMillis(2_000L);
        ledger.join(ALICE, "AliceNew");
        advanceMillis(3_000L);

        assertEquals(new TimeLedger.Row(ALICE, "AliceNew", 5_000L, true), alice());
    }

    @Test
    void offlineTimeIsExcludedAndReconnectContinuesSeededTotal() {
        ledger.seed(ALICE, "Alice", 60_000L);
        ledger.join(ALICE, "Alice");
        advanceMillis(20_000L);
        ledger.leave(ALICE);
        ledger.leave(ALICE);
        ledger.leave(BOB);
        advanceMillis(600_000L);
        assertEquals(new TimeLedger.Row(ALICE, "Alice", 80_000L, false), alice());

        ledger.join(ALICE, "Alice");
        advanceMillis(10_000L);
        assertEquals(90_000L, alice().totalMillis());
    }

    @Test
    void pauseFreezesAllSessionsAndResumeDoesNotCatchUp() {
        ledger.join(ALICE, "Alice");
        advanceMillis(2_000L);
        ledger.setPaused(true);
        advanceMillis(10_000L);
        ledger.setPaused(true);
        assertEquals(new TimeLedger.Row(ALICE, "Alice", 2_000L, true), alice());

        ledger.setPaused(false);
        advanceMillis(3_000L);
        ledger.setPaused(false);
        advanceMillis(4_000L);
        assertEquals(9_000L, alice().totalMillis());
    }

    @Test
    void joiningWhilePausedDoesNotStartCountingUntilResume() {
        ledger.setPaused(true);
        ledger.join(ALICE, "Alice");
        advanceMillis(10_000L);
        assertEquals(0L, alice().totalMillis());

        ledger.setPaused(false);
        advanceMillis(1_000L);
        assertEquals(1_000L, alice().totalMillis());
    }

    @Test
    void lanMenuWithUnpausedServerKeepsCounting() {
        ledger.join(ALICE, "Alice");
        ledger.setPaused(false);
        advanceMillis(120_000L);

        assertEquals(120_000L, alice().totalMillis());
    }

    @Test
    void closingSessionsCommitsOnceAndStopsCounting() {
        ledger.join(ALICE, "Alice");
        ledger.join(BOB, "Bob");
        advanceMillis(2_000L);
        ledger.closeSessions();
        advanceMillis(10_000L);
        ledger.closeSessions();

        assertEquals(2, ledger.snapshot().size());
        assertTrue(ledger.snapshot().stream().allMatch(row -> !row.online() && row.totalMillis() == 2_000L));
    }

    @Test
    void restoringBackupRewindsTotalsAndDiscardsFuturePlayersAndAnchors() {
        ledger.seed(ALICE, "Alice", 60_000L);
        ledger.join(ALICE, "Alice");
        advanceMillis(10_000L);
        List<TimeLedger.Row> backup = ledger.snapshot();
        advanceMillis(30_000L);
        ledger.join(BOB, "Bob");
        ledger.setPaused(true);
        ledger.restore(backup);
        advanceMillis(600_000L);

        assertFalse(ledger.contains(BOB));
        assertEquals(new TimeLedger.Row(ALICE, "Alice", 70_000L, false), alice());
        ledger.join(ALICE, "Alice");
        advanceMillis(5_000L);
        assertEquals(75_000L, alice().totalMillis());
    }

    @Test
    void newlyStartedServerIgnoresSavedOnlineFlagAndShutdownGap() {
        ledger.join(ALICE, "Alice");
        advanceMillis(30_000L);
        List<TimeLedger.Row> saved = ledger.snapshot();
        advanceMillis(3_600_000L);
        TimeLedger restarted = new TimeLedger(clock::get);
        restarted.restore(saved);

        assertEquals(new TimeLedger.Row(ALICE, "Alice", 30_000L, false), restarted.snapshot().getFirst());
        restarted.join(ALICE, "Alice");
        advanceMillis(5_000L);
        assertEquals(35_000L, restarted.snapshot().getFirst().totalMillis());
    }

    @Test
    void fractionsSurviveReconnectAndPauseWithinCurrentSession() {
        ledger.join(ALICE, "Alice");
        clock.addAndGet(600_000L);
        ledger.leave(ALICE);
        ledger.join(ALICE, "Alice");
        clock.addAndGet(600_000L);
        ledger.setPaused(true);
        assertEquals(1L, alice().totalMillis());
        clock.addAndGet(100_000_000L);
        ledger.setPaused(false);
        clock.addAndGet(800_000L);

        assertEquals(2L, alice().totalMillis());
    }

    @Test
    void monotonicClockWraparoundStillCountsElapsedTime() {
        clock.set(Long.MAX_VALUE - 1_500_000L);
        ledger.join(ALICE, "Alice");
        clock.addAndGet(3_000_000L);

        assertEquals(3L, alice().totalMillis());
    }

    @Test
    void durationCannotOverflowIntoNegativeTime() {
        ledger.seed(ALICE, "Alice", Long.MAX_VALUE - 1L);
        ledger.join(ALICE, "Alice");
        advanceMillis(10L);

        assertEquals(Long.MAX_VALUE, alice().totalMillis());
        ledger.leave(ALICE);
        assertEquals(Long.MAX_VALUE, alice().totalMillis());
    }

    @Test
    void invalidRestoreDoesNotDestroyExistingWorldState() {
        ledger.seed(ALICE, "Alice", 60_000L);
        assertThrows(NullPointerException.class,
                () -> ledger.restore(java.util.Arrays.asList(new TimeLedger.Row(BOB, "Bob", 0L, false), null)));
        assertEquals(new TimeLedger.Row(ALICE, "Alice", 60_000L, false), alice());
        assertFalse(ledger.contains(BOB));
        assertThrows(IllegalArgumentException.class, () -> ledger.seed(BOB, "Bob", -1L));
    }

    private TimeLedger.Row alice() {
        return ledger.snapshot().stream().filter(row -> row.uuid().equals(ALICE)).findFirst().orElseThrow();
    }

    private void advanceMillis(long millis) {
        clock.addAndGet(millis * 1_000_000L);
    }
}
