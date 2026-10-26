package net.wiknzylix.playtimetogether;

import net.wiknzylix.playtimetogether.core.TimeLedger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LedgerStoreTest {
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    @TempDir Path directory;

    @Test
    void savesWorldIdentityAndDurationsAndKeepsThePreviousValidSnapshot() throws Exception {
        Path file = directory.resolve("data/ledger.json");
        try (var store = new LedgerStore(file)) {
            store.flush(document(100_000));
            store.flush(document(200_000));
        }
        try (var reopened = new LedgerStore(file);
             var backup = new LedgerStore(file.resolveSibling("ledger.json.bak"))) {
            assertEquals(document(200_000), reopened.load());
            assertEquals(document(100_000), backup.load());
        }
        assertFalse(Files.exists(file.resolveSibling("ledger.json.tmp")));
    }

    @Test
    void damagedPrimaryRecoversBackupAndDoesNotReplaceItWithDamagedData() throws Exception {
        Path file = directory.resolve("ledger.json");
        try (var store = new LedgerStore(file)) {
            store.flush(document(100_000));
            store.flush(document(200_000));
        }
        Files.writeString(file, "{broken");
        try (var store = new LedgerStore(file)) {
            assertEquals(document(100_000), store.load());
            store.flush(document(110_000));
        }
        try (var primary = new LedgerStore(file);
             var backup = new LedgerStore(file.resolveSibling("ledger.json.bak"))) {
            assertEquals(document(110_000), primary.load());
            assertEquals(document(100_000), backup.load());
        }
    }

    @Test
    void refusesToOverwriteHistoryWhenBothCopiesAreDamaged() throws Exception {
        Path file = directory.resolve("ledger.json");
        Files.writeString(file, "{broken");
        Files.writeString(file.resolveSibling("ledger.json.bak"), "also broken");
        try (var store = new LedgerStore(file)) {
            assertThrows(IOException.class, store::load);
        }
        assertEquals("{broken", Files.readString(file));
    }

    @Test
    void failedWriteCompletesTheBarrierAndCanBeRetried() throws Exception {
        Path parent = directory.resolve("blocked");
        Files.writeString(parent, "not a directory");
        try (var store = new LedgerStore(parent.resolve("ledger.json"))) {
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(5),
                    () -> assertThrows(IOException.class, () -> store.flush(document(100_000))));
            Files.delete(parent);
            store.flush(document(110_000));
            assertEquals(document(110_000), store.load());
        }
    }

    @Test
    void legacyJsonWithoutFakeMetadataLoadsAsAnEmptyImmutableMap() throws Exception {
        Path file = directory.resolve("ledger.json");
        Files.writeString(file, """
                {"version":1,"worldId":"%s","players":[
                {"uuid":"%s","name":"Alice","totalMillis":12345,"online":false}]}
                """.formatted(WORLD, PLAYER));
        try (var store = new LedgerStore(file)) {
            var loaded = store.load();
            assertEquals(document(12_345), loaded);
            assertTrue(loaded.fakePlayers().isEmpty());
            assertTrue(loaded.realPlayers().isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> loaded.fakePlayers().put(PLAYER, true));
            assertThrows(UnsupportedOperationException.class, () -> loaded.realPlayers().add(PLAYER));
        }
    }

    @Test
    void explicitNullMetadataAlsoDefaultsToEmpty() throws Exception {
        Path file = directory.resolve("ledger.json");
        Files.writeString(file, """
                {"version":1,"worldId":"%s","players":[],"fakePlayers":null,"realPlayers":null}
                """.formatted(WORLD));
        try (var store = new LedgerStore(file)) {
            assertTrue(store.load().fakePlayers().isEmpty());
            assertTrue(store.load().realPlayers().isEmpty());
        }
    }

    @Test
    void legacyFakeFlagsDoNotClaimEvidenceOfRealSessions() throws Exception {
        Path file = directory.resolve("ledger.json");
        Files.writeString(file, """
                {"version":1,"worldId":"%s","players":[],"fakePlayers":{"%s":false}}
                """.formatted(WORLD, PLAYER));
        try (var store = new LedgerStore(file)) {
            assertEquals(Map.of(PLAYER, false), store.load().fakePlayers());
            assertTrue(store.load().realPlayers().isEmpty());
        }
    }

    @Test
    void metadataSurvivesSavingOfflinePlayersWithoutRetainingMutableCallerState() throws Exception {
        Path file = directory.resolve("ledger.json");
        UUID real = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Map<UUID, Boolean> metadata = new HashMap<>(Map.of(PLAYER, true, real, false));
        Set<UUID> realPlayers = new HashSet<>(Set.of(real));
        var saved = new LedgerStore.Document(1, WORLD,
                List.of(new TimeLedger.Row(PLAYER, "OfflineBot", 50_000, false),
                        new TimeLedger.Row(real, "RealPlayer", 60_000, false)), metadata, realPlayers);
        metadata.clear(); realPlayers.clear();
        assertEquals(Map.of(PLAYER, true, real, false), saved.fakePlayers());
        assertEquals(Set.of(real), saved.realPlayers());
        assertThrows(UnsupportedOperationException.class, () -> saved.realPlayers().add(PLAYER));
        try (var store = new LedgerStore(file)) { store.flush(saved); }
        try (var store = new LedgerStore(file)) {
            assertEquals(saved, store.load());
            assertFalse(store.load().players().getFirst().online());
            assertTrue(store.load().fakePlayers().get(PLAYER));
            assertEquals(Set.of(real), store.load().realPlayers());
        }
    }

    @Test
    void realHistoryAndTheLastFakeSessionRoundTripIndependently() throws Exception {
        Path file = directory.resolve("ledger.json");
        var saved = new LedgerStore.Document(1, WORLD, document(50_000).players(), Map.of(PLAYER, true), Set.of(PLAYER));
        try (var store = new LedgerStore(file)) { store.flush(saved); }
        try (var store = new LedgerStore(file)) {
            assertEquals(saved, store.load());
            var history = new net.wiknzylix.playtimetogether.core.PlayerIdentityHistory();
            history.restore(store.load().fakePlayers(), store.load().realPlayers());
            assertTrue(history.isFakePlayer(PLAYER, true));
            assertFalse(history.isFakePlayer(PLAYER, false));
        }
    }

    @Test
    void restoringAnOlderWorldRewindsFakeMetadataAlongWithDurations() throws Exception {
        Path file = directory.resolve("ledger.json");
        UUID futureBot = UUID.fromString("00000000-0000-0000-0000-000000000003");
        byte[] checkpoint;
        var original = new LedgerStore.Document(1, WORLD, document(100_000).players(), Map.of(PLAYER, true));
        try (var store = new LedgerStore(file)) {
            store.flush(original);
            checkpoint = Files.readAllBytes(file);
            store.flush(new LedgerStore.Document(1, WORLD,
                    List.of(new TimeLedger.Row(PLAYER, "Alice", 200_000, false),
                            new TimeLedger.Row(futureBot, "FutureBot", 30_000, false)), Map.of(futureBot, true), Set.of(PLAYER, futureBot)));
        }
        Files.write(file, checkpoint);
        Files.deleteIfExists(file.resolveSibling("ledger.json.bak"));
        try (var restored = new LedgerStore(file)) {
            assertEquals(original, restored.load());
            assertFalse(restored.load().fakePlayers().containsKey(futureBot));
            assertTrue(restored.load().realPlayers().isEmpty(), "Future real-player evidence must also roll back");
        }
    }

    @Test
    void interruptedShutdownStillDrainsWriterBeforeRollbackAndRejectsOldWork() throws Exception {
        Path file = directory.resolve("data/ledger.json");
        LedgerStore oldWorld = new LedgerStore(file);
        oldWorld.flush(document(100_000));
        byte[] backupSnapshot = Files.readAllBytes(file);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        AtomicBoolean interruptPreserved = new AtomicBoolean();
        // Hold the I/O executor so shutdown is guaranteed to encounter an unfinished write.
        var field = LedgerStore.class.getDeclaredField("writer");
        field.setAccessible(true);
        ExecutorService writer = (ExecutorService) field.get(oldWorld);
        writer.execute(() -> {
            entered.countDown();
            try { release.await(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        oldWorld.request(document(200_000));
        Thread shutdown = new Thread(() -> {
            Thread.currentThread().interrupt();
            oldWorld.close();
            interruptPreserved.set(Thread.currentThread().isInterrupted());
            closed.countDown();
        });
        try {
            shutdown.start();
            assertFalse(closed.await(100, TimeUnit.MILLISECONDS),
                    "Returning before the writer exits would let future data overwrite a restored world");
        } finally {
            release.countDown();
        }
        assertTrue(closed.await(5, TimeUnit.SECONDS));
        assertTrue(interruptPreserved.get());
        // Model QB restoring an older world after the old server has fully stopped.
        Files.write(file, backupSnapshot);
        Files.deleteIfExists(file.resolveSibling("ledger.json.bak"));
        try (var restoredWorld = new LedgerStore(file)) {
            assertEquals(document(100_000), restoredWorld.load());
            oldWorld.request(document(300_000));
            assertThrows(IOException.class, () -> oldWorld.flush(document(300_000)));
            assertEquals(document(100_000), restoredWorld.load());
        }
    }

    private static LedgerStore.Document document(long millis) {
        return new LedgerStore.Document(1, WORLD,
                List.of(new TimeLedger.Row(PLAYER, "Alice", millis, false)));
    }
}
