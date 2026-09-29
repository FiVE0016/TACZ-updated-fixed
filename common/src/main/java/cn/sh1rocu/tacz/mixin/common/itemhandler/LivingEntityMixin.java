package cn.sh1rocu.tacz.mixin.common.itemhandler;

import cn.sh1rocu.tacz.api.mixin.ItemHandlerCapability;
import cn.sh1rocu.tacz.util.forge.LazyOptional;
import cn.sh1rocu.tacz.util.itemhandler.IItemHandler;
import cn.sh1rocu.tacz.util.itemhandler.entity.EntityEquipmentInvWrapper;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements ItemHandlerCapability {
    @Shadow
    public abstract boolean isAlive();

    @Unique
    private LazyOptional<?>[] handlers;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void tacz$initClass(EntityType<? extends LivingEntity> entityType, Level world, CallbackInfo ci) {
        handlers = EntityEquipmentInvWrapper.create(tacz$self());
    }

    @Override
    public LazyOptional<IItemHandler> tacz$getItemHandler(@Nullable Direction facing) {
        return tacz$getEquipmentItemHandler(facing);
    }

    @Override
    public LazyOptional<IItemHandler> tacz$getEquipmentItemHandler(@Nullable Direction facing) {
        if (isAlive()) {
            if (facing == null) {
                return this.handlers[2].cast();
            }

            if (facing.getAxis().isVertical()) {
                return this.handlers[0].cast();
            }

            if (facing.getAxis().isHorizontal()) {
                return this.handlers[1].cast();
            }
        }

        return LazyOptional.empty();
    }

    @Override
    public void tacz$invalidateItemHandler() {
        tacz$invalidateEquipmentItemHandlers();
    }

    @Override
    public void tacz$invalidateEquipmentItemHandlers() {
        for (LazyOptional<?> handler : this.handlers) {
            handler.invalidate();
        }
    }

    @Override
    public void tacz$reviveItemHandler() {
        this.handlers = EntityEquipmentInvWrapper.create(tacz$self());
    }

    /**
     * Prefixed on purpose: Forge's {@code IForgeLivingEntity} and NeoForge's {@code ILivingEntityExtension} give
     * {@code LivingEntity} a public {@code self()}, and a private method of that name here hides it from the JVM,
     * so every living entity would crash with an {@code AbstractMethodError} on its first tick.
     */
    @Unique
    private LivingEntity tacz$self() {
        return (LivingEntity) (Object) this;
    }
}
