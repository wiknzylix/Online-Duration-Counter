package net.wiknzylix.playtimetogether.mixin;

import net.minecraft.server.MinecraftServer;
import net.wiknzylix.playtimetogether.PlaytimeService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Joins the existing save barrier used by QB, without changing world save logic. */
@Mixin(MinecraftServer.class)
public abstract class ServerSaveMixin {
    @Inject(method = "save(ZZZ)Z", at = @At("HEAD"))
    private void odc$beforeWorldSave(boolean quiet, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
        var s = PlaytimeService.get((MinecraftServer) (Object) this);
        if (s != null) {
            s.observePause(((MinecraftServer) (Object) this).isPaused());
            s.save(true, force);
        }
    }
}
