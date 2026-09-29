package cn.sh1rocu.tacz.compat.meshloader.mixin;

import cn.sh1rocu.tacz.compat.meshloader.render.ScreenRenderTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brackets a screen's extract pass for {@link ScreenRenderTracker}, in place of Fabric's
 * {@code ScreenEvents.beforeExtract/afterExtract}.
 *
 * <p>Fabric wraps the call site in {@code Gui#extractRenderState}; NeoForge and Forge route that call
 * through their own screen render hooks, so the method itself is the one point all loaders share.</p>
 */
@Mixin(Screen.class)
public abstract class ScreenExtractTrackerMixin {

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"))
    private void meshyloader$beginScreenExtract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a,
                                                CallbackInfo ci) {
        ScreenRenderTracker.setExtractingScreen(true);
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("RETURN"))
    private void meshyloader$endScreenExtract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a,
                                              CallbackInfo ci) {
        ScreenRenderTracker.setExtractingScreen(false);
    }
}
