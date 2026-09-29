package cn.sh1rocu.tacz.mixin.common;

import cn.sh1rocu.tacz.api.event.LivingHurtEvent;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires TACZ's {@link LivingHurtEvent} from {@code LivingEntity#actuallyHurt}, before armor reduces the damage.
 * <p>
 * Not applied on NeoForge (see {@link cn.sh1rocu.tacz.util.CompatMixinPlugin}): NeoForge rewrites
 * {@code actuallyHurt} around its damage container, so the second injection would run before the first there.
 * NeoForge fires the event from its own {@code LivingIncomingDamageEvent} instead.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {
    @ModifyVariable(method = "actuallyHurt", at = @At(value = "LOAD", ordinal = 0), index = 3)
    private float tacz$livingHurtEvent(float value, ServerLevel level, DamageSource pDamageSource, @Share("hurt") LocalRef<LivingHurtEvent> eventRef) {
        LivingHurtEvent event = new LivingHurtEvent((LivingEntity) (Object) this, pDamageSource, value);
        eventRef.set(event);
        LivingHurtEvent.CALLBACK.invoker().onLivingHurt(event);
        if (event.isCanceled())
            return 0;
        return event.getAmount();
    }

    @Inject(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"), cancellable = true)
    private void tacz$shouldCancelHurt(ServerLevel level, DamageSource damageSource, float f, CallbackInfo ci, @Share("hurt") LocalRef<LivingHurtEvent> eventRef) {
        if (eventRef.get().getAmount() <= 0)
            ci.cancel();
    }
}
