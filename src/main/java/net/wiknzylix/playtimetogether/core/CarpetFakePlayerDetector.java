package net.wiknzylix.playtimetogether.core;

/** Optional Carpet compatibility without linking or loading any Carpet classes. */
public final class CarpetFakePlayerDetector {
    private static final String FAKE_PLAYER_CLASS = "carpet.patches.EntityPlayerMPFake";

    private CarpetFakePlayerDetector() { }

    /**
     * Carpet 1.4.194 for Minecraft 1.21.11 uses this ServerPlayer subclass for
     * spawned players, shadows and respawns. Subclasses from extensions also match.
     * https://github.com/gnembon/fabric-carpet/blob/1.4.194/src/main/java/carpet/patches/EntityPlayerMPFake.java
     */
    public static boolean isFakePlayer(Object player) {
        for (Class<?> type = player == null ? null : player.getClass(); type != null; type = type.getSuperclass()) {
            if (FAKE_PLAYER_CLASS.equals(type.getName())) return true;
        }
        return false;
    }
}
