package net.wiknzylix.playtimetogether.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import net.wiknzylix.playtimetogether.PlaytimePayload;
import net.wiknzylix.playtimetogether.PlaytimeSnapshot;
import net.wiknzylix.playtimetogether.PlaytimeTogether;
import org.lwjgl.glfw.GLFW;

public final class PlaytimeClient implements ClientModInitializer {
    private static final Comparator<DisplayEntry> ORDER = Comparator.comparingLong(DisplayEntry::totalMillis).reversed()
        .thenComparing(DisplayEntry::name, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(DisplayEntry::name)
        .thenComparing(entry -> entry.uuid().toString());
    private static ClientConfig config;
    private static PlaytimeSnapshot snapshot;
    private static long receivedAtNanos;
    private static EntryCache filteredCache;
    private static EntryCache allCache;
    private static KeyBinding openConfiguration;
    private static boolean migrateKeyOnFirstTick;

    @Override
    public void onInitializeClient() {
        config = ClientConfig.load();
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(PlaytimeTogether.MOD_ID, "general"));
        openConfiguration = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.playtime_together.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, category));
        migrateKeyOnFirstTick = config.consumeConfigurationKeyMigration();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (migrateKeyOnFirstTick) {
                migrateKeyOnFirstTick = false;
                if (openConfiguration.isUnbound()) {
                    openConfiguration.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_Y));
                    KeyBinding.updateKeysByCode();
                    client.options.write();
                }
                config.save();
            }
            while (openConfiguration.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new PlaytimeConfigScreen(client.currentScreen));
                }
            }
        });
        // Fabric invokes PlayPayloadHandler on the render thread. Avoid a second queued task
        // that could otherwise run after DISCONNECT/JOIN has cleared the session cache.
        ClientPlayNetworking.registerGlobalReceiver(PlaytimePayload.ID, (payload, context) -> receive(payload.snapshot()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
        HudElementRegistry.addLast(Identifier.of(PlaytimeTogether.MOD_ID, "sidebar"), (context, tickCounter) -> PlaytimeHud.render(context));
    }

    private static void receive(PlaytimeSnapshot incoming) {
        // A world change discards the previous world's entire snapshot and interpolation origin.
        snapshot = incoming;
        receivedAtNanos = System.nanoTime();
        filteredCache = null;
        allCache = null;
    }

    private static void clear() {
        snapshot = null;
        receivedAtNanos = 0L;
        filteredCache = null;
        allCache = null;
        PlaytimeHud.clearCache();
    }

    public static ClientConfig config() {
        if (config == null) config = ClientConfig.load();
        return config;
    }

    public static PlaytimeSnapshot snapshot() { return snapshot; }

    public static KeyBinding configurationKey() { return openConfiguration; }

    public static List<DisplayEntry> entries(boolean applyFilter) {
        if (snapshot == null) return List.of();
        // This estimate only smooths the display between server snapshots. It is never persisted or uploaded.
        long elapsed = snapshot.paused() ? 0L : Math.max(0L, (System.nanoTime() - receivedAtNanos) / 1_000_000L);
        // Both minute and second formats refresh at most once per second. Rendering reuses the same
        // immutable list between buckets, rather than allocating and sorting on every video frame.
        long bucket = elapsed / 1000L;
        long preferences = applyFilter ? config().displayRevision() : 0L;
        EntryCache cache = applyFilter ? filteredCache : allCache;
        if (cache != null && cache.source == snapshot && cache.bucket == bucket && cache.preferences == preferences) return cache.entries;
        List<DisplayEntry> entries = new ArrayList<>();
        for (PlaytimeSnapshot.Entry entry : snapshot.entries()) {
            if (applyFilter && !config().includes(snapshot.worldId(), entry.uuid(), entry.name(), entry.fakePlayer())) continue;
            long base = Math.max(0L, entry.totalMillis());
            long displayed = entry.online() ? (Long.MAX_VALUE - base < elapsed ? Long.MAX_VALUE : base + elapsed) : base;
            entries.add(new DisplayEntry(entry.uuid(), entry.name(), displayed, entry.online(), entry.fakePlayer()));
        }
        entries.sort(ORDER);
        List<DisplayEntry> result = List.copyOf(entries);
        EntryCache updated = new EntryCache(snapshot, bucket, preferences, result);
        if (applyFilter) filteredCache = updated;
        else allCache = updated;
        return result;
    }

    public record DisplayEntry(UUID uuid, String name, long totalMillis, boolean online, boolean fakePlayer) {
        public DisplayEntry(UUID uuid, String name, long totalMillis, boolean online) { this(uuid, name, totalMillis, online, false); }
    }
    private record EntryCache(PlaytimeSnapshot source, long bucket, long preferences, List<DisplayEntry> entries) { }
}
