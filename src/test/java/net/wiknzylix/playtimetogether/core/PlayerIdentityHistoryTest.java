package net.wiknzylix.playtimetogether.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerIdentityHistoryTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void aFakeSessionDoesNotRelabelObservedRealHistoryAfterLeaving() {
        var history = new PlayerIdentityHistory();
        assertTrue(history.observe(PLAYER, false));
        assertFalse(history.isFakePlayer(PLAYER, true));
        assertTrue(history.observe(PLAYER, true));
        assertTrue(history.isFakePlayer(PLAYER, true), "The current fake session can still be hidden");
        assertFalse(history.isFakePlayer(PLAYER, false), "Offline real history must become visible again");
        assertEquals(Map.of(PLAYER, true), history.fakePlayers());
        assertEquals(Set.of(PLAYER), history.realPlayers());
    }

    @Test
    void fakeOnlyPlayersRemainIdentifiableAfterLeavingAndRejoining() {
        var history = new PlayerIdentityHistory();
        assertTrue(history.observe(PLAYER, true));
        assertTrue(history.isFakePlayer(PLAYER, true));
        assertTrue(history.isFakePlayer(PLAYER, false));
        assertFalse(history.observe(PLAYER, true));
        assertTrue(history.isFakePlayer(PLAYER, false));
        assertTrue(history.realPlayers().isEmpty());
    }

    @Test
    void aRealSessionSupersedesFakeOnlyHistoryAndIsRetainedForLaterFakes() {
        var history = new PlayerIdentityHistory();
        history.observe(PLAYER, true);
        assertTrue(history.observe(PLAYER, false));
        assertFalse(history.isFakePlayer(PLAYER, true));
        assertFalse(history.isFakePlayer(PLAYER, false));
        assertTrue(history.fakePlayers().isEmpty());
        history.observe(PLAYER, true);
        assertTrue(history.isFakePlayer(PLAYER, true));
        assertFalse(history.isFakePlayer(PLAYER, false));
    }

    @Test
    void theFirstRealObservationIsPersistableEvenWithoutAnExistingFakeFlag() {
        var history = new PlayerIdentityHistory();
        assertTrue(history.observe(PLAYER, false));
        assertFalse(history.observe(PLAYER, false));
        assertEquals(Set.of(PLAYER), history.realPlayers());
    }

    @Test
    void unknownAndLegacyFalseFlagsDoNotInventRealPlayerEvidence() {
        var history = new PlayerIdentityHistory();
        assertFalse(history.isFakePlayer(PLAYER, false));
        history.restore(Map.of(PLAYER, false), Set.of());
        assertTrue(history.realPlayers().isEmpty());
        history.observe(PLAYER, true);
        assertTrue(history.isFakePlayer(PLAYER, false));
        assertTrue(history.realPlayers().isEmpty());
    }

    @Test
    void restoringAnOlderWorldReplacesBothKindsOfEvidence() {
        var history = new PlayerIdentityHistory();
        history.observe(PLAYER, false);
        history.observe(OTHER, true);
        history.restore(Map.of(PLAYER, true), Set.of());
        assertTrue(history.isFakePlayer(PLAYER, false), "Real evidence from the future must not survive rollback");
        assertFalse(history.fakePlayers().containsKey(OTHER));
        assertTrue(history.realPlayers().isEmpty());
        history.restore(Map.of(PLAYER, true), Set.of(PLAYER));
        assertFalse(history.isFakePlayer(PLAYER, false));
        assertTrue(history.isFakePlayer(PLAYER, true));
    }

    @Test
    void restoredInputsAndReturnedSnapshotsCannotMutateTheHistory() {
        var fake = new HashMap<>(Map.of(PLAYER, true));
        var real = new HashSet<>(Set.of(PLAYER));
        var history = new PlayerIdentityHistory();
        history.restore(fake, real);
        fake.clear(); real.clear();
        var savedFake = history.fakePlayers();
        var savedReal = history.realPlayers();
        assertThrows(UnsupportedOperationException.class, () -> savedFake.put(OTHER, true));
        assertThrows(UnsupportedOperationException.class, () -> savedReal.add(OTHER));
        history.observe(PLAYER, false);
        history.observe(OTHER, false);
        assertEquals(Map.of(PLAYER, true), savedFake);
        assertEquals(Set.of(PLAYER), savedReal);
        assertEquals(Set.of(PLAYER, OTHER), history.realPlayers());
    }

    @Test
    void evidenceIsAlwaysKeyedByTheActualUuid() {
        var history = new PlayerIdentityHistory();
        history.observe(PLAYER, false);
        history.observe(OTHER, true);
        assertFalse(history.isFakePlayer(PLAYER, false));
        assertTrue(history.isFakePlayer(OTHER, false));
        assertThrows(NullPointerException.class, () -> history.observe(null, false));
    }
}
