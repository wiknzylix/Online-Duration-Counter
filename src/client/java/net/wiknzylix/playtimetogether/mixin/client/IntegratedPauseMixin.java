package net.wiknzylix.playtimetogether.mixin.client;

import net.minecraft.server.integrated.IntegratedServer;
import net.wiknzylix.playtimetogether.PlaytimeService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observation only: never cancels, redirects or modifies a game tick. */
@Mixin(IntegratedServer.class)
public abstract class IntegratedPauseMixin {
    @Inject(method = "processPacketsAndTick", at = @At("RETURN"))
    private void odc$observePause(boolean sprint, CallbackInfo ci) {
        var server = (IntegratedServer) (Object) this;
        var service = PlaytimeService.get(server);
        if (service != null) service.observePause(server.isPaused());
    }
}
