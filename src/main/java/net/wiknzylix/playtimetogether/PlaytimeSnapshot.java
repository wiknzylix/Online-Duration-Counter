package net.wiknzylix.playtimetogether;

import java.util.List;
import java.util.UUID;

public record PlaytimeSnapshot(UUID worldId, long revision, boolean paused, List<Entry> entries) {
    public PlaytimeSnapshot { entries = List.copyOf(entries); }
    public record Entry(UUID uuid, String name, long totalMillis, boolean online, boolean fakePlayer) {
        public Entry(UUID uuid, String name, long totalMillis, boolean online) {
            this(uuid, name, totalMillis, online, false);
        }
    }
}
