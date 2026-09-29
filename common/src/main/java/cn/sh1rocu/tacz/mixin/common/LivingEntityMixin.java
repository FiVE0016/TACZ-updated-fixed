package cn.sh1rocu.tacz.mixin.common;

import cn.sh1rocu.tacz.api.event.LivingKnockBackEvent;
import cn.sh1rocu.tacz.api.extension.IItem;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.tacz.guns.init.ModAttributes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    public abstract ItemStack getItemInHand(InteractionHand hand);

    @ModifyReturnValue(method = "createLivingAttributes", at = @At("RETURN"))
    private static AttributeSupplier.Builder tacz$createLivingAttributes(AttributeSupplier.Builder original) {
        return original.add(BuiltInRegistries.ATTRIBUTE.wrapAsHolder(ModAttributes.BULLET_RESISTANCE));
    }

    /**
     * 26.3: {@code swing} 多了 {@code SwingAnimation} 参数，返回值也从 void 变成 boolean
     * —— 方法体是 {@code return swingState.startIfAble(...)}，即「这一次挥手有没有真的开始」
     * （字节码实读）。所以 TACZ 压掉挥手时要返回 {@code false}，语义与 26.2 的
     * {@code ci.cancel()} 一致：这次挥手没发生。
     */
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void tacz$swingHand(InteractionHand hand, SwingAnimation swingAnimation, boolean bl,
                                CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = this.getItemInHand(hand);
        if (!stack.isEmpty() && stack.getItem() instanceof IItem swing) {
            if (swing.tacz$onEntitySwing(stack, (LivingEntity) (Object) this))
                cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private double tacz$modifyKnockbackStrength(double strength, double ogstrength, double xRatio, double zRatio, @Share("event") LocalRef<LivingKnockBackEvent> eventRef) {
        LivingKnockBackEvent event = new LivingKnockBackEvent((LivingEntity) (Object) this, (float) strength, xRatio, zRatio);
        LivingKnockBackEvent.CALLBACK.invoker().onLivingKnockBack(event);
        eventRef.set(event);
        if (!event.isCanceled() && event.getOriginalStrength() != event.getStrength()) {
            return event.getStrength();
        }
        return strength;
    }

    @ModifyVariable(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private double tacz$modifyRatioX(double ratioX, @Share("event") LocalRef<LivingKnockBackEvent> eventRef) {
        var event = eventRef.get();
        if (event.getOriginalRatioX() != event.getRatioX())
            return event.getRatioX();
        return ratioX;
    }

    @ModifyVariable(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private double tacz$modifyRatioZ(double ratioZ, @Share("event") LocalRef<LivingKnockBackEvent> eventRef) {
        var event = eventRef.get();
        if (event.getOriginalRatioZ() != event.getRatioZ())
            return event.getRatioZ();
        return ratioZ;
    }

    @Inject(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getAttributeValue(Lnet/minecraft/core/Holder;)D"), cancellable = true)
    private void tacz$shouldCancelKnockback(double strength, double xRatio, double zRatio, DamageSource source, float damage, boolean comesFromEffect, CallbackInfo ci, @Share("event") LocalRef<LivingKnockBackEvent> eventRef) {
        if (eventRef.get().isCanceled())
            ci.cancel();
    }
}
