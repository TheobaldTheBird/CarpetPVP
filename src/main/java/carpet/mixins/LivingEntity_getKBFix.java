package carpet.mixins;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntity_getKBFix {

    @Inject(
            method = "getKnockback(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)F",
            at = @At("HEAD"),
            cancellable = true
    )
    private void modifyKnockback(Entity entity, DamageSource damageSource, CallbackInfoReturnable<Float> cir) {
        if (entity instanceof LivingEntity target && target.invulnerableTime < 20) {
            cir.setReturnValue(0.0F);
            return;
        }

        float baseKnockback = (float) ((LivingEntity) (Object) this).getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        Level level = ((LivingEntity) (Object) this).level();
        if (level instanceof ServerLevel serverLevel) {
            float modifiedKnockback = EnchantmentHelper.modifyKnockback(serverLevel, ((LivingEntity) (Object) this).getMainHandItem(), entity, damageSource, baseKnockback);
            cir.setReturnValue(modifiedKnockback);
        } else {
            cir.setReturnValue(baseKnockback);
        }
    }
}
