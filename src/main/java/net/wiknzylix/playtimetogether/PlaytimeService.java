package net.wiknzylix.playtimetogether;

import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.WorldSavePath;
import net.wiknzylix.playtimetogether.core.TimeLedger;
import net.wiknzylix.playtimetogether.core.CarpetFakePlayerDetector;
import net.wiknzylix.playtimetogether.core.PlayerIdentityHistory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class PlaytimeService {
    private static final Map<MinecraftServer, PlaytimeService> SERVICES = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final long PERIOD = 60_000_000_000L;
    private final MinecraftServer server;
    private final TimeLedger ledger = new TimeLedger(System::nanoTime);
    private final PlayerIdentityHistory identities = new PlayerIdentityHistory();
    private final Set<UUID> activeSessions = new HashSet<>();
    private final LedgerStore store;
    private UUID worldId;
    private long revision, nextSync;
    private boolean paused, closed;

    private PlaytimeService(MinecraftServer server) throws IOException {
        this.server = server;
        store = new LedgerStore(server.getSavePath(WorldSavePath.ROOT).resolve("data/odc-online-duration.json"));
        try {
            var saved = store.load(); worldId = saved.worldId(); ledger.restore(saved.players());
            identities.restore(saved.fakePlayers(), saved.realPlayers());
            importHistoricalStats();
            store.flush(document());
        } catch (Exception e) { store.close(); throw new IOException("ODC cannot safely open this world's ledger", e); }
        nextSync = System.nanoTime() + PERIOD;
    }
    public static void start(MinecraftServer server) {
        try { SERVICES.put(server, new PlaytimeService(server)); }
        catch (IOException e) { PlaytimeTogether.LOGGER.error("ODC disabled for this world to protect its saved history", e); }
    }
    public static PlaytimeService get(MinecraftServer server) { return SERVICES.get(server); }
    public static void stop(MinecraftServer server) {
        PlaytimeService s = SERVICES.remove(server);
        if (s == null) return;
        s.closed = true;
        s.activeSessions.clear();
        s.ledger.closeSessions();
        try { s.store.flush(s.document()); }
        catch (IOException e) { PlaytimeTogether.LOGGER.error("ODC final save failed", e); }
        finally { s.store.close(); }
    }
    private void importHistoricalStats() {
        Path stats = server.getSavePath(WorldSavePath.STATS);
        if (!Files.isDirectory(stats)) return;
        Map<UUID, String> names = new HashMap<>();
        // Cache only; never contact Mojang or a skin server to infer account identity.
        Path cache = server.getRunDirectory().resolve("usercache.json");
        if (Files.exists(cache)) {
            try (var r = Files.newBufferedReader(cache, StandardCharsets.UTF_8)) {
                for (var e : JsonParser.parseReader(r).getAsJsonArray()) {
                    var o = e.getAsJsonObject(); names.put(UUID.fromString(o.get("uuid").getAsString()), o.get("name").getAsString());
                }
            } catch (Exception e) { PlaytimeTogether.LOGGER.debug("ODC could not read optional name cache", e); }
        }
        try (var files = Files.list(stats)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                try {
                    String fn = file.getFileName().toString(); UUID id = UUID.fromString(fn.substring(0, fn.length() - 5));
                    if (ledger.contains(id)) continue;
                    try (var r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        var root = JsonParser.parseReader(r).getAsJsonObject();
                        long ticks = 0;
                        if (root.has("stats") && root.getAsJsonObject("stats").has("minecraft:custom")) {
                            var custom = root.getAsJsonObject("stats").getAsJsonObject("minecraft:custom");
                            if (custom.has("minecraft:play_time")) ticks = Math.max(0, custom.get("minecraft:play_time").getAsLong());
                        }
                        ledger.seed(id, names.getOrDefault(id, "玩家·" + id.toString().substring(0, 8)), Math.multiplyExact(ticks, 50L));
                    }
                } catch (Exception e) { PlaytimeTogether.LOGGER.warn("ODC skipped an unreadable historical statistic: {}", file, e); }
            }
        } catch (IOException e) { PlaytimeTogether.LOGGER.warn("ODC could not import historical stats", e); }
    }
    public void join(ServerPlayerEntity player) {
        if (closed) return;
        if (!ledger.contains(player.getUuid())) {
            long ticks = Math.max(0, player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME)));
            ledger.seed(player.getUuid(), player.getName().getString(), ticks * 50L);
        }
        ledger.join(player.getUuid(), player.getName().getString());
        activeSessions.add(player.getUuid());
        rememberPlayerType(player);
        paused = server.isPaused(); ledger.setPaused(paused);
        broadcast(); save(false);
    }
    public void leave(ServerPlayerEntity player) {
        // Both Fabric DISCONNECT and PlayerManager.remove may observe one departure.
        // Carpet can reach remove without Fabric's connection-specific callback.
        if (closed || !activeSessions.remove(player.getUuid())) return;
        rememberPlayerType(player);
        ledger.leave(player.getUuid()); broadcast(); save(false);
    }
    /** Update from an actual server entity after an explicit metadata/lifecycle change. */
    public void updatePlayerMetadata(ServerPlayerEntity player) {
        if (closed || !ledger.contains(player.getUuid())) return;
        if (rememberPlayerType(player)) { broadcast(); save(false); }
    }
    private boolean rememberPlayerType(ServerPlayerEntity player) {
        return identities.observe(player.getUuid(), CarpetFakePlayerDetector.isFakePlayer(player));
    }
    public void observePause(boolean value) {
        if (closed || value == paused) return;
        paused = value; ledger.setPaused(value); broadcast();
    }
    public void tick() {
        if (closed) return;
        observePause(server.isPaused());
        long now = System.nanoTime();
        if (now - nextSync >= 0) {
            nextSync = now + PERIOD; broadcast();
            if (!server.getOverworld().savingDisabled) save(false);
        }
    }
    private PlaytimeSnapshot snapshot() {
        var rows = ledger.snapshot().stream().map(r -> new PlaytimeSnapshot.Entry(r.uuid(), r.name(), r.totalMillis(), r.online(),
            identities.isFakePlayer(r.uuid(), r.online()))).toList();
        return new PlaytimeSnapshot(worldId, ++revision, paused, rows);
    }
    private void broadcast() {
        var payload = new PlaytimePayload(snapshot());
        for (var player : server.getPlayerManager().getPlayerList())
            if (ServerPlayNetworking.canSend(player, PlaytimePayload.ID)) ServerPlayNetworking.send(player, payload);
    }
    private LedgerStore.Document document() {
        return new LedgerStore.Document(1, worldId, ledger.snapshot(), identities.fakePlayers(), identities.realPlayers());
    }
    public void save(boolean flush) {
        save(flush, false);
    }
    /** Explicit forced world saves (including QB's backup barrier) also save our ledger. */
    public void save(boolean flush, boolean force) {
        if (closed || (!force && server.getOverworld().savingDisabled)) return;
        if (flush) {
            try { store.flush(document()); }
            catch (IOException e) { PlaytimeTogether.LOGGER.error("ODC could not flush before world backup/save", e); }
        } else store.request(document());
    }
}
