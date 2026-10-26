package net.wiknzylix.playtimetogether.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * World-local, authoritative playtime. Only accumulated durations are persisted;
 * nanoTime anchors belong to the current server session and must never be saved.
 */
public final class TimeLedger {
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final LongSupplier clock;
    private final Map<UUID, Entry> players = new LinkedHashMap<>();
    private boolean paused;

    public TimeLedger(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Imports historical playtime once per UUID. */
    public synchronized void seed(UUID uuid, String name, long totalMillis) {
        Row row = new Row(uuid, name, totalMillis, false);
        players.putIfAbsent(uuid, new Entry(row));
    }

    /** A duplicate join updates the name without restarting or doubling a session. */
    public synchronized void join(UUID uuid, String name) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        Entry entry = players.computeIfAbsent(uuid,
                ignored -> new Entry(new Row(uuid, name, 0L, false)));
        entry.name = name;
        if (!entry.online) {
            entry.anchorNanos = clock.getAsLong();
            entry.online = true;
        }
    }

    public synchronized void leave(UUID uuid) {
        Entry entry = players.get(uuid);
        if (entry != null && entry.online) {
            commit(entry, clock.getAsLong());
            entry.online = false;
        }
    }

    /**
     * The caller supplies the actual server pause state. An open-to-LAN world
     * normally stays unpaused even while the host has the pause menu open.
     */
    public synchronized void setPaused(boolean paused) {
        if (this.paused == paused) {
            return;
        }
        long now = clock.getAsLong();
        for (Entry entry : players.values()) {
            if (entry.online) {
                if (paused) {
                    commit(entry, now);
                } else {
                    entry.anchorNanos = now;
                }
            }
        }
        this.paused = paused;
    }

    /** Returns an immutable view evaluated against a single clock reading. */
    public synchronized List<Row> snapshot() {
        long now = clock.getAsLong();
        List<Row> rows = new ArrayList<>(players.size());
        for (Map.Entry<UUID, Entry> item : players.entrySet()) {
            Entry entry = item.getValue();
            rows.add(new Row(item.getKey(), entry.name,
                    projectedMillis(entry, elapsedNanos(entry, now)), entry.online));
        }
        return List.copyOf(rows);
    }

    public synchronized boolean contains(UUID uuid) {
        return players.containsKey(uuid);
    }

    /** Finalizes all active sessions for a normal server shutdown. */
    public synchronized void closeSessions() {
        long now = clock.getAsLong();
        for (Entry entry : players.values()) {
            if (entry.online) {
                commit(entry, now);
                entry.online = false;
            }
        }
    }

    /**
     * Replaces all state with a saved world snapshot, including when restoring
     * an older backup. Saved online flags are deliberately ignored. No future
     * playtime or old session anchors may survive a restore.
     */
    public synchronized void restore(List<Row> rows) {
        Objects.requireNonNull(rows, "rows");
        Map<UUID, Entry> restored = new LinkedHashMap<>();
        for (Row row : rows) {
            Objects.requireNonNull(row, "row");
            restored.put(row.uuid(), new Entry(row));
        }
        players.clear();
        players.putAll(restored);
        paused = false;
    }

    private long elapsedNanos(Entry entry, long now) {
        if (!entry.online || paused) {
            return 0L;
        }
        // Subtraction also handles the signed nanoTime wraparound correctly.
        return Math.max(0L, now - entry.anchorNanos);
    }

    private long projectedMillis(Entry entry, long elapsedNanos) {
        long extraMillis = elapsedNanos / NANOS_PER_MILLI
                + (elapsedNanos % NANOS_PER_MILLI + entry.remainderNanos) / NANOS_PER_MILLI;
        return extraMillis > Long.MAX_VALUE - entry.totalMillis
                ? Long.MAX_VALUE : entry.totalMillis + extraMillis;
    }

    private void commit(Entry entry, long now) {
        long elapsed = elapsedNanos(entry, now);
        entry.totalMillis = projectedMillis(entry, elapsed);
        entry.remainderNanos = (elapsed % NANOS_PER_MILLI + entry.remainderNanos) % NANOS_PER_MILLI;
        entry.anchorNanos = now;
    }

    public record Row(UUID uuid, String name, long totalMillis, boolean online) {
        public Row {
            Objects.requireNonNull(uuid, "uuid");
            Objects.requireNonNull(name, "name");
            if (totalMillis < 0L) {
                throw new IllegalArgumentException("totalMillis must not be negative");
            }
        }
    }

    private static final class Entry {
        private String name;
        private long totalMillis;
        private long remainderNanos;
        private long anchorNanos;
        private boolean online;

        private Entry(Row row) {
            name = row.name();
            totalMillis = row.totalMillis();
        }
    }
}
