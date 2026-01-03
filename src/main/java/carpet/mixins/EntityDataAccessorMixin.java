package carpet.mixins;

import carpet.CarpetSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.commands.data.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(EntityDataAccessor.class)

public class EntityDataAccessorMixin {
    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "setData", at = @At("HEAD"), cancellable = true)
    void allowPlayerNbtSet(CompoundTag nbt, CallbackInfo ci) {
        if (!CarpetSettings.editablePlayerNbt) return;
        if (this.entity instanceof Player) {
            UUID UUID = this.entity.getUUID();
            this.entity.load(nbt);
            this.entity.setUUID(UUID);
            ci.cancel();
        }
    }
}
