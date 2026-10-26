package net.wiknzylix.playtimetogether.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.wiknzylix.playtimetogether.core.PlayerRuleMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** All options in this file are local preferences. No value is sent to the server. */
public final class ClientConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("ODC/ClientConfig");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("playtime_together-client.json");

    public int schemaVersion = 3;
    public boolean enabled = true;
    public int width = 60;
    public int height = 142;
    public double fontScale = 0.35;
    /** Normalized position over the available travel range; stable across GUI scale changes. */
    public double positionX = 1.0;
    public double positionY = 1.0;
    public int backgroundOpacity = 30;
    public DurationFormat timeFormat = DurationFormat.HOURS_MINUTES;
    public int titleColor = 0xF1F1F1;
    public int subtitleColor = 0xB5B5B5;
    public int onlineColor = 0x9DB79B;
    public int offlineColor = 0x909090;
    public Map<String, WorldFilter> worlds = new HashMap<>();
    private transient long displayRevision;
    private transient boolean migrateConfigurationKey;

    public long displayRevision() { return displayRevision; }

    /** Invalidate local presentation caches immediately after an in-memory preference edit. */
    public void changed() { displayRevision++; }

    public static ClientConfig load() {
        if (Files.isRegularFile(FILE)) {
            try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                ClientConfig result = GSON.fromJson(reader, ClientConfig.class);
                if (result != null) {
                    result.sanitize();
                    return result;
                }
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Could not read ODC client preferences; using defaults", exception);
            }
        }
        return new ClientConfig();
    }

    public void sanitize() {
        if (schemaVersion < 2) {
            // Adopt the compact layout only for old defaults, keeping deliberate custom settings.
            if (width == 240) width = 120;
            if (height == 220) height = 300;
            if (fontScale == 1.0) fontScale = 0.8;
            schemaVersion = 2;
        }
        if (schemaVersion < 3) {
            if (width == 120) width = 60;
            if (height == 300) height = 142;
            if (fontScale == 0.8) fontScale = 0.35;
            if (positionX == 1.0 && positionY == 0.5) positionY = 1.0;
            migrateConfigurationKey = true;
            schemaVersion = 3;
        }
        width = Math.clamp(width, 60, 1200);
        height = Math.clamp(height, 40, 1200);
        fontScale = Double.isFinite(fontScale) ? Math.clamp(fontScale, 0.35, 3.0) : 0.35;
        positionX = Double.isFinite(positionX) ? Math.clamp(positionX, 0.0, 1.0) : 1.0;
        positionY = Double.isFinite(positionY) ? Math.clamp(positionY, 0.0, 1.0) : 1.0;
        titleColor &= 0xFFFFFF;
        subtitleColor &= 0xFFFFFF;
        onlineColor &= 0xFFFFFF;
        offlineColor &= 0xFFFFFF;
        backgroundOpacity = Math.clamp(backgroundOpacity, 0, 100);
        if (timeFormat == null) timeFormat = DurationFormat.HOURS_MINUTES;
        if (worlds == null) worlds = new HashMap<>();
        worlds.entrySet().removeIf(entry -> !validUuid(entry.getKey()) || entry.getValue() == null);
        for (WorldFilter filter : worlds.values()) {
            if (filter.selected == null) filter.selected = new HashSet<>();
            filter.selected.removeIf(value -> !validUuid(value));
            if (filter.identities == null) filter.identities = new HashMap<>();
            filter.identities.entrySet().removeIf(entry -> !validUuid(entry.getKey()) || entry.getValue() == null
                || entry.getValue() == IdentityOverride.AUTO);
            if (filter.blockedKeywords == null) filter.blockedKeywords = "";
            if (filter.blockedRegex == null) filter.blockedRegex = "";
        }
    }

    public boolean consumeConfigurationKeyMigration() {
        boolean result = migrateConfigurationKey;
        migrateConfigurationKey = false;
        return result;
    }

    private static boolean validUuid(String value) {
        if (value == null) return false;
        try { return UUID.fromString(value).toString().equals(value); }
        catch (IllegalArgumentException exception) { return false; }
    }

    public WorldFilter filter(UUID worldId) {
        return worlds.computeIfAbsent(worldId.toString(), ignored -> new WorldFilter());
    }

    public boolean includes(UUID worldId, UUID playerId) {
        WorldFilter filter = worlds.get(worldId.toString());
        return filter == null || filter.showAll || filter.selected.contains(playerId.toString());
    }

    public boolean includes(UUID worldId, UUID playerId, String name, boolean fakePlayer) {
        WorldFilter filter = worlds.get(worldId.toString());
        return includes(worldId, playerId) && (filter == null || !filter.isBlocked(playerId, name, fakePlayer));
    }

    public void restoreDisplayDefaults() {
        enabled = true;
        schemaVersion = 3;
        width = 60;
        height = 142;
        fontScale = 0.35;
        positionX = 1.0;
        positionY = 1.0;
        backgroundOpacity = 30;
        timeFormat = DurationFormat.HOURS_MINUTES;
        changed();
    }

    public void restoreColorDefaults() {
        titleColor = 0xF1F1F1;
        subtitleColor = 0xB5B5B5;
        onlineColor = 0x9DB79B;
        offlineColor = 0x909090;
        changed();
    }

    public void save() {
        sanitize();
        Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(temporary, GSON.toJson(this) + System.lineSeparator(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not save ODC client preferences", exception);
        }
    }

    public enum IdentityOverride {
        AUTO, REAL, FAKE;
        public IdentityOverride next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public static final class WorldFilter {
        public boolean showAll = true;
        public Set<String> selected = new HashSet<>();
        public Map<String, IdentityOverride> identities = new HashMap<>();
        public boolean autoBlockRules = true;
        public String blockedKeywords = "";
        public String blockedRegex = "";
        public boolean blockNumericIds;
        public boolean blockFakePlayers;
        private transient PlayerRuleMatcher matcher;
        private transient String compiledKeywords;
        private transient String compiledRegex;
        private transient boolean compiledNumeric;
        private transient boolean compiledFake;

        private PlayerRuleMatcher matcher() {
            String keywords = blockedKeywords == null ? "" : blockedKeywords;
            String regex = blockedRegex == null ? "" : blockedRegex;
            if (matcher == null || !keywords.equals(compiledKeywords) || !regex.equals(compiledRegex)
                || blockNumericIds != compiledNumeric || blockFakePlayers != compiledFake) {
                matcher = new PlayerRuleMatcher(keywords, regex, blockNumericIds, blockFakePlayers);
                compiledKeywords = keywords;
                compiledRegex = regex;
                compiledNumeric = blockNumericIds;
                compiledFake = blockFakePlayers;
            }
            return matcher;
        }

        public boolean matches(String name, boolean fakePlayer) { return matcher().matches(name, fakePlayer); }
        public IdentityOverride identity(UUID playerId) {
            return identities == null ? IdentityOverride.AUTO : identities.getOrDefault(playerId.toString(), IdentityOverride.AUTO);
        }
        public void setIdentity(UUID playerId, IdentityOverride identity) {
            if (identities == null) identities = new HashMap<>();
            if (identity == null || identity == IdentityOverride.AUTO) identities.remove(playerId.toString());
            else identities.put(playerId.toString(), identity);
        }
        public boolean effectiveFake(UUID playerId, boolean serverFake) {
            return switch (identity(playerId)) {
                case REAL -> false;
                case FAKE -> true;
                case AUTO -> serverFake;
            };
        }
        public boolean matches(UUID playerId, String name, boolean serverFake) {
            return matcher().matches(name, effectiveFake(playerId, serverFake));
        }
        public String regexError() { return matcher().regexError(); }
        public boolean isBlocked(String name, boolean fakePlayer) { return autoBlockRules && matches(name, fakePlayer); }
        public boolean isBlocked(UUID playerId, String name, boolean serverFake) {
            return autoBlockRules && matches(playerId, name, serverFake);
        }
    }
}
