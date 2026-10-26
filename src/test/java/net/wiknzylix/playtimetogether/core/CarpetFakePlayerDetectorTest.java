package net.wiknzylix.playtimetogether.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CarpetFakePlayerDetectorTest {
    @Test
    void recognizesTheExactCarpetClassAndExtensionSubclasses() {
        assertTrue(CarpetFakePlayerDetector.isFakePlayer(new carpet.patches.EntityPlayerMPFake()));
        assertTrue(CarpetFakePlayerDetector.isFakePlayer(new ExtensionFake()));
    }

    @Test
    void doesNotInferFakePlayersFromDisplayNamesOrSimpleClassNames() {
        assertFalse(CarpetFakePlayerDetector.isFakePlayer(new EntityPlayerMPFake()));
        assertFalse(CarpetFakePlayerDetector.isFakePlayer("carpet.patches.EntityPlayerMPFake"));
        assertFalse(CarpetFakePlayerDetector.isFakePlayer(new Object()));
        assertFalse(CarpetFakePlayerDetector.isFakePlayer(null));
    }

    private static final class ExtensionFake extends carpet.patches.EntityPlayerMPFake { }
    private static final class EntityPlayerMPFake { }
}
