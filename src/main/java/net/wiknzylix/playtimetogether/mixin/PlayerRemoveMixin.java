package net.wiknzylix.playtimetogether.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.wiknzylix.playtimetogether.PlaytimeService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes the final player-removal lifecycle, including Carpet fake players. */
@Mixin(PlayerManager.class)
public abstract class PlayerRemoveMixin {
    @Shadow @Final private MinecraftServer server;

    @Inject(method = "remove(Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("HEAD"))
    private void odc$beforePlayerRemoved(ServerPlayerEntity player, CallbackInfo ci) {
        PlaytimeService service = PlaytimeService.get(server);
        if (service != null) service.leave(player);
    }
}
