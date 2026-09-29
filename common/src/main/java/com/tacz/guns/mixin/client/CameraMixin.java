package com.tacz.guns.mixin.client;

import cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Produces the previously orphaned camera/FOV events from Minecraft 26.2's Camera API. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private Level level;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float tacz$modifyWorldFov(float original, float partialTick) {
        Camera self = (Camera) (Object) this;
        ViewportEvent.ComputeFov event = new ViewportEvent.ComputeFov(self, partialTick, true, original);
        ViewportEvent.FOV.invoker().onComputeFov(event);
        return (float) event.getFOV();
    }

    @ModifyReturnValue(method = "calculateHudFov", at = @At("RETURN"))
    private float tacz$modifyHandFov(float original, float partialTick) {
        Camera self = (Camera) (Object) this;
        ViewportEvent.ComputeFov event = new ViewportEvent.ComputeFov(self, partialTick, false, original);
        ViewportEvent.FOV.invoker().onComputeFov(event);
        return (float) event.getFOV();
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void tacz$applyCameraAnimations(DeltaTracker deltaTracker, CallbackInfo ci) {
        // update() only moves the camera while there is a level and a player, and Forge and NeoForge call it on
        // every frame from the moment the game window opens.
        if (this.level == null || Minecraft.getInstance().player == null) {
            return;
        }
        Camera self = (Camera) (Object) this;
        float partialTick = self.getCameraEntityPartialTicks(deltaTracker);
        ViewportEvent.ComputeCameraAngles event = new ViewportEvent.ComputeCameraAngles(
                self, partialTick, self.yRot(), self.xRot(), 0.0F
        );
        ViewportEvent.CAMERA.invoker().onComputeCameraAngles(event);
        this.setRotation(event.getYaw(), event.getPitch());
        if (event.getRoll() != 0.0F) {
            self.rotation().mul(Axis.ZP.rotationDegrees(event.getRoll()));
        }
    }
}
