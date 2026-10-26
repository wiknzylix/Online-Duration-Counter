package net.wiknzylix.playtimetogether;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlaytimePayloadTest {
    @Test
    void versionTwoRoundTripPreservesOnlineAndOfflineFakeFlags() {
        var snapshot = new PlaytimeSnapshot(UUID.randomUUID(), 42, true, List.of(
                new PlaytimeSnapshot.Entry(UUID.randomUUID(), "Real", 10_000, true, false),
                new PlaytimeSnapshot.Entry(UUID.randomUUID(), "OnlineBot", 20_000, true, true),
                new PlaytimeSnapshot.Entry(UUID.randomUUID(), "OfflineBot", 30_000, false, true)));
        RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            var payload = new PlaytimePayload(snapshot);
            PlaytimePayload.CODEC.encode(buffer, payload);
            assertEquals(payload, PlaytimePayload.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
            assertEquals("snapshot_v2", PlaytimePayload.ID.id().getPath());
        } finally { buffer.release(); }
    }
}
