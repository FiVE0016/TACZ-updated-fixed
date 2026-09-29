package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.compat.iris.IrisScopeMaskState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Writes the TACZ scope-mask uniforms when Iris sets up an ExtendedShader program.
 *
 * <p>Iris runs {@code ProgramSamplers#update()} and {@code ProgramUniforms#update()},
 * which reset every custom uniform back to its default. This hook runs after that
 * reset and re-applies the scope-mask mode, so the value survives until the draw
 * call - see {@code IrisScopeMaskState#applyToShaderProgram}.</p>
 *
 * <p>MC 26.3 / Iris: the hook signature is {@code (List, CallbackInfo)}.
 * It used to be {@code (HashMap, GpuTextureView, CallbackInfo)}; when that drifted,
 * {@code require = 0} let the injector fail silently and the scope-mask mode stayed
 * 0 at draw time, which produced the black lens. {@code require = 1} makes any
 * future drift fail loudly at startup instead of silently disabling the feature.</p>
 */
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ExtendedShader", remap = false)
public abstract class IrisExtendedShaderMixin {
    @Inject(method = "iris$setupState", at = @At("RETURN"), require = 1)
    private void tacz$setupScopeMaskUniforms(List<?> samplers, CallbackInfo ci) {
        IrisScopeMaskState.applyToShaderProgram((Object) this);
    }
}
