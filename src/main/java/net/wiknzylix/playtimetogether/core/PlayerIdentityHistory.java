package net.wiknzylix.playtimetogether.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** World-local evidence from actual player entities, never from names or UUID patterns. */
public final class PlayerIdentityHistory {
    private final Map<UUID, Boolean> fakePlayers = new HashMap<>();
    private final Set<UUID> realPlayers = new HashSet<>();

    /** Replaces all evidence when a world is opened, including an older restored backup. */
    public void restore(Map<UUID, Boolean> fakePlayers, Set<UUID> realPlayers) {
        Map<UUID, Boolean> restoredFake = Map.copyOf(fakePlayers);
        Set<UUID> restoredReal = Set.copyOf(realPlayers);
        this.fakePlayers.clear();
        this.fakePlayers.putAll(restoredFake);
        this.realPlayers.clear();
        this.realPlayers.addAll(restoredReal);
    }

    /** Records an observed entity type and returns whether persistent evidence changed. */
    public boolean observe(UUID uuid, boolean fakePlayer) {
        Objects.requireNonNull(uuid, "uuid");
        boolean wasFake = Boolean.TRUE.equals(fakePlayers.get(uuid));
        boolean newRealEvidence = !fakePlayer && realPlayers.add(uuid);
        if (fakePlayer) fakePlayers.put(uuid, true);
        else fakePlayers.remove(uuid);
        return wasFake != fakePlayer || newRealEvidence;
    }

    /** Online rows describe this session; offline rows preserve any observed real-player history. */
    public boolean isFakePlayer(UUID uuid, boolean online) {
        return Boolean.TRUE.equals(fakePlayers.get(uuid)) && (online || !realPlayers.contains(uuid));
    }

    /** Last observed entity types, retained separately from real-player history. */
    public Map<UUID, Boolean> fakePlayers() { return Map.copyOf(fakePlayers); }

    /** UUIDs observed as actual non-Carpet entities in this world's recorded history. */
    public Set<UUID> realPlayers() { return Set.copyOf(realPlayers); }
}
