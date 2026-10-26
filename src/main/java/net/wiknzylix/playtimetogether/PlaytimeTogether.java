package net.wiknzylix.playtimetogether;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlaytimeTogether implements ModInitializer {
    public static final String MOD_ID = "playtime_together";
    public static final Logger LOGGER = LoggerFactory.getLogger("ODC");
    @Override public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(PlaytimePayload.ID, PlaytimePayload.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(PlaytimeService::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(PlaytimeService::stop);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var s = PlaytimeService.get(server); if (s != null) s.tick();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var s = PlaytimeService.get(server); if (s != null) s.join(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var s = PlaytimeService.get(server); if (s != null) s.leave(handler.player);
        });
    }
}
