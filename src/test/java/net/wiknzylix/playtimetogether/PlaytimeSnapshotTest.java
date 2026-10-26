package net.wiknzylix.playtimetogether;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlaytimeSnapshotTest {
    @Test
    void legacyConstructorDefaultsToRealWhileExplicitOfflineFakeIsRetained() {
        UUID player = UUID.randomUUID();
        assertFalse(new PlaytimeSnapshot.Entry(player, "Player", 1_000, true).fakePlayer());
        var offlineFake = new PlaytimeSnapshot.Entry(player, "Bot", 2_000, false, true);
        var snapshot = new PlaytimeSnapshot(UUID.randomUUID(), 1, false, List.of(offlineFake));
        assertTrue(snapshot.entries().getFirst().fakePlayer());
        assertFalse(snapshot.entries().getFirst().online());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.entries().clear());
    }
}
