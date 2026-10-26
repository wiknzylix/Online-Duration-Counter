package net.wiknzylix.playtimetogether;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.wiknzylix.playtimetogether.core.TimeLedger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One world, one writer. No queued old snapshot may survive closing this store. */
public final class LedgerStore implements AutoCloseable {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public record Document(int version, UUID worldId, List<TimeLedger.Row> players, Map<UUID, Boolean> fakePlayers,
                           Set<UUID> realPlayers) {
        public Document {
            players = List.copyOf(players);
            // Gson supplies null for this optional field when loading a pre-1.0.2 ledger.
            fakePlayers = fakePlayers == null ? Map.of() : Map.copyOf(fakePlayers);
            // Older ledgers cannot prove whether an unmarked UUID ever belonged to a real session.
            realPlayers = realPlayers == null ? Set.of() : Set.copyOf(realPlayers);
        }
        public Document(int version, UUID worldId, List<TimeLedger.Row> players, Map<UUID, Boolean> fakePlayers) {
            this(version, worldId, players, fakePlayers, Set.of());
        }
        public Document(int version, UUID worldId, List<TimeLedger.Row> players) {
            this(version, worldId, players, Map.of(), Set.of());
        }
    }
    private final Path path;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ODC-save"); t.setDaemon(true); return t;
    });
    private Document pending;
    private boolean running, closed;
    private IOException failure;

    public LedgerStore(Path path) { this.path = path; }
    public Document load() throws IOException {
        if (!Files.exists(path) && !Files.exists(backup())) return new Document(1, UUID.randomUUID(), List.of());
        try { return read(path); }
        catch (Exception primary) {
            try {
                Document restored = read(backup());
                PlaytimeTogether.LOGGER.warn("ODC restored its backup ledger after a damaged main file", primary);
                return restored;
            } catch (Exception secondary) { throw new IOException("ODC ledger and backup are unreadable; refusing to overwrite history", primary); }
        }
    }
    private Document read(Path file) throws IOException {
        Document d;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { d = JSON.fromJson(reader, Document.class); }
        if (d == null || d.version() != 1 || d.worldId() == null || d.players() == null)
            throw new IOException("Invalid or newer ODC ledger: " + file);
        for (var r : d.players()) if (r.uuid() == null || r.name() == null || r.totalMillis() < 0) throw new IOException("Invalid ODC player row");
        return d;
    }
    public synchronized void request(Document document) {
        if (closed) return;
        pending = document; // bounded: one current write, one replaceable next write
        if (!running) { running = true; writer.execute(this::drain); }
    }
    private void drain() {
        while (true) {
            Document next;
            synchronized (this) {
                next = pending; pending = null;
                if (next == null) { running = false; notifyAll(); return; }
            }
            try { write(next); synchronized (this) { failure = null; } }
            catch (IOException | RuntimeException e) {
                synchronized (this) { failure = e instanceof IOException io ? io : new IOException("ODC ledger write failed", e); }
                PlaytimeTogether.LOGGER.error("ODC could not save online durations", e);
            }
        }
    }
    private void write(Document d) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, JSON.toJson(d), StandardCharsets.UTF_8);
        // Back up only a validated file; a recovered ledger must not lose its good backup.
        if (Files.exists(path)) {
            try { read(path); Files.copy(path, backup(), StandardCopyOption.REPLACE_EXISTING); }
            catch (RuntimeException | IOException invalid) { PlaytimeTogether.LOGGER.warn("ODC retained the previous valid backup", invalid); }
        }
        try { Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING); }
    }
    private Path backup() { return path.resolveSibling(path.getFileName() + ".bak"); }
    public synchronized void flush(Document d) throws IOException {
        if (closed) throw new IOException("ODC ledger store is already closed");
        request(d);
        while (running) {
            try { wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Interrupted ODC flush", e); }
        }
        if (failure != null) throw failure;
    }
    @Override public synchronized void close() {
        // A disconnected world's writer must finish before QB deletes/restores the world.
        // Reject new work first, and never return early merely because shutdown was interrupted.
        closed = true;
        boolean interrupted = false;
        while (running) {
            try { wait(); } catch (InterruptedException e) { interrupted = true; }
        }
        writer.shutdown();
        if (interrupted) Thread.currentThread().interrupt();
    }
}
