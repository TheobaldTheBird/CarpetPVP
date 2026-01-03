package carpet.mixins;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayer.class)
public class ServerPlayer_fixPassengersMixin {
    @Redirect(
            method = "setGameMode",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;stopRiding()V"
            )
    )
    private void fixPassengers(ServerPlayer player) {
        if (!player.getPassengers().isEmpty()) {
            player.ejectPassengers();
        }
    }
}
