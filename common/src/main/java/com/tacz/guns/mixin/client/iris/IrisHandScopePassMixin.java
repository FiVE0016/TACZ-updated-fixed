package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.client.render.scope.ScopePipRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 光影下，镜内那一遍<b>不要画手部/枪身</b>。
 *
 * <h2>为什么需要它</h2>
 *
 * 无光影路径里，手部渲染发生在 {@code LevelRenderer#render} 之后（vanilla 的
 * {@code renderItemInHand} 在 {@code renderLevel} 后面），所以镜内那一遍
 * （自己调一次 {@code LevelRenderer#render}）天然只有世界、没有枪。
 *
 * 但 Iris 把手部渲染搬进了 {@code LevelRenderer#render} 内部：
 * {@code MixinLevelRenderer} 会调 {@code HandRenderer.INSTANCE.renderSolid/renderTranslucent}
 * （1.11.7 字节码实读）。于是镜内那一遍也会把枪身画进去 ——
 * 2026-10-10 实机探针证实：镜内画面正中央有一个黑色圆盘（镜身目镜），
 * 合成从这张图里采样，镜片自然就是黑的。
 *
 * <p>本 mixin 在镜内那一遍（{@link ScopePipRenderer#isInsideScopeLevelRender()} 为真）时
 * 取消手部渲染，让镜内画面回到「纯世界」；主画面那一遍不受影响，枪照画。</p>
 *
 * <h2>为什么处理器只带 CallbackInfo</h2>
 *
 * 这两个方法的参数里有 Iris 自己的类型（{@code WorldRenderingPipeline}），
 * 而本项目刻意不把 Iris 放进编译类路径（iris mixin 全部通过 {@code targets} 字符串
 * 定位，避免硬依赖）。HEAD + cancellable 的处理器不声明方法参数即可合法注入。
 *
 * <h2>为什么用 require = 1</h2>
 *
 * 与 {@code IrisLevelFinalScopePipMixin} 同一政策：若 Iris 将来改了
 * {@code HandRenderer} 的方法签名，宁可启动时报错，也不要静默退回「黑镜片」。
 */
@Mixin(targets = "net.irisshaders.iris.pathways.HandRenderer", remap = false)
public abstract class IrisHandScopePassMixin {

    @Inject(method = "renderSolid", at = @At("HEAD"), cancellable = true, require = 1)
    private void tacz$skipSolidHandInScopePass(CallbackInfo ci) {
        if (ScopePipRenderer.isInsideScopeLevelRender()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderTranslucent", at = @At("HEAD"), cancellable = true, require = 1)
    private void tacz$skipTranslucentHandInScopePass(CallbackInfo ci) {
        if (ScopePipRenderer.isInsideScopeLevelRender()) {
            ci.cancel();
        }
    }
}
