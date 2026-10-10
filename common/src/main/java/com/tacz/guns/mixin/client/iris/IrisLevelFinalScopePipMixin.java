package com.tacz.guns.mixin.client.iris;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopePipRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 光影下，把「抓取镜内素材」与「镜内放大合成」接进 Iris 的收工时刻。
 *
 * <h2>为什么必须挂在这里（而 LevelRenderer#render 之后不行）</h2>
 *
 * 26.3 的移植里，这两步挂在 {@code GameRendererMixin} 的
 * {@code LevelRenderer#render} 调用 <b>AFTER</b>，理由写在注释里：
 * 「Iris 把手部渲染搬进了 LevelRenderer#render 内部，所以此刻整条 Iris 管线已经收工」。
 *
 * <p><b>实机证明这个前提不成立。</b>2026-10-10 的测试（Fabric 26.3 + Iris 1.11.7 +
 * Complementary）：两个模式都产出黑镜片。</p>
 *
 * <h2>根因（2026-10-10 字节码实读确认）</h2>
 *
 * <ul>
 *   <li>{@code GlCommandEncoder#copyTextureToTexture} 是<b>立即执行</b>的
 *       {@code glBlitFramebuffer} —— 读的是源纹理【此刻】的内容；</li>
 *   <li>{@code IrisRenderingPipeline#finalizeLevelRendering} 里的
 *       {@code FinalPassRenderer#renderFinalPass} 用 {@code createRenderPass}
 *       <b>记录</b>命令，稍后才执行；</li>
 *   <li>vanilla 26.3 的帧图在 {@code LevelRenderer#render} <b>内部</b>执行
 *       （{@code FrameGraphBuilder.execute} 偏移 769），Iris 的 finalize 挂在它之后 ——
 *       所以 final pass 是在帧图跑完之后才被记录的，真正执行更晚。</li>
 * </ul>
 *
 * <p>结论：任何「在 finalize 之后立刻 copyTextureToTexture」的代码都抢跑，
 * 拿到的是上一帧的旧画面（开镜瞬间"有内容"=旧帧、开满后"格子"=旧帧中心放大 25 倍）。</p>
 *
 * <h2>这里的修法</h2>
 *
 * <ol>
 *   <li>镜内那一遍的 finalize 触发时，记录一次<b>延迟 blit 拷贝</b>
 *       （{@code ScopePipRenderer#captureScopePassAfterIrisFinal}）—— 它在命令流里
 *       排在镜内最终合成<b>之后</b>执行，拿到镜内真画面；</li>
 *   <li>主画面那一遍的 finalize 触发时，照常抓重投影素材 + 合成镜片。</li>
 * </ol>
 *
 * <h2>为什么用 require = 1</h2>
 *
 * 这是「修好 vs 静默退化」的开关：若将来 Iris 改了 {@code finalizeLevelRendering} 的名字或签名，
 * 注入失败必须<b>在启动时大声报错</b>，而不是悄悄退回旧行为、让黑镜片重新出现却无人知情。
 * 同类教训已经写过一次 —— 见 {@code IrisExtendedShaderMixin} 的类注释。
 */
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class IrisLevelFinalScopePipMixin {

    /** 「本次会话已播报过」一次性标志，避免每帧刷屏。 */
    private static boolean tacz$loggedDeferral;

    @Inject(method = "finalizeLevelRendering", at = @At("TAIL"), require = 1)
    private void tacz$captureAndCompositeAfterIrisFinal(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        try {
            // 【甲】镜内那一遍的收工：此刻记录一次延迟拷贝。
            // 注意：scopePassReadyToCapture 在 renderScopeView 里、levelRenderer.render()
            // 调用【之前】就已置位，所以这里能看到它（前几轮把它放在 render 之后，
            // 导致这里每次都静默跳过 —— 那是路线 B 纯黑的直接原因之一）。
            if (ScopePipRenderer.isInsideScopeLevelRender()) {
                ScopePipRenderer.captureScopePassAfterIrisFinal();
                // 窄遍只负责拷画面；合成与延迟覆盖层留给主画面那一遍。
                return;
            }

            // 【乙】主画面那一遍的收工。这个守卫防的是「镜内那一遍还没完全退出」的
            // 窗口（isInsideScopeLevelRender 已在 finally 里复位，但保险起见）。
            if (ScopePipRenderer.isScopePassActive()) {
                return;
            }

            // 重投影模式：抓主画面素材（延迟 blit），随后合成。
            ScopePipRenderer.captureScene(mc);
            if (!tacz$loggedDeferral) {
                tacz$loggedDeferral = true;
                GunMod.LOGGER.info("[TACZ Scope] Scope PIP now captures the scene after Iris' final composite "
                        + "(IrisRenderingPipeline#finalizeLevelRendering TAIL), not right after LevelRenderer#render.");
            }
            ScopePipRenderer.compositeAfterLevelUnderShaders();
        } catch (Throwable t) {
            // 绝不让这里的异常打断整个渲染管线：出错就退回旧行为（镜内不放大），
            // 而不是让玩家看到崩溃。
            GunMod.LOGGER.error("[TACZ Scope] Deferred scope PIP capture failed; "
                    + "falling back to whole-screen FOV zoom for this session.", t);
        }
    }
}
