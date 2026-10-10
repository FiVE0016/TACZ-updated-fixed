package com.tacz.guns.compat.distanthorizons;

import com.tacz.guns.GunMod;
import com.tacz.guns.platform.Platform;

import java.lang.reflect.Field;

/**
 * Distant Horizons（地平线模组）的投影同步。
 *
 * <h2>问题：DH 每帧只认<b>第一次</b>投影写入</h2>
 *
 * DH 挂在 {@code ProjectionMatrixBuffer#writeBuffer} 上捕获 MC 投影
 * （{@code MixinProjectionMatrixBufferCommon.onMatrixWrite}，3.3.4 字节码实读），
 * 但捕获受一次性旗标 {@code getNewMvmMatrix} 把关：每帧只在旗标为 true 时
 * 接受<b>一次</b>写入，捕获后立刻置 false。而帧里的顺序是：
 *
 * <pre>
 * vanilla renderLevel 设置    → writeBuffer(主投影) → DH 捕获宽 FOV → 旗标耗尽
 * TACZ 镜内那一遍             → writeBuffer(窄投影) → 旗标已 false → DH 忽略
 * </pre>
 *
 * 于是镜内那一遍的 LOD 远景永远用宽 FOV 的投影画 —— 实测症状正是
 * 「近景跟着倍率放大、远处地平线纹丝不动」。光影下没有这个问题，
 * 因为 Iris 路径里 DH 走自己的 Iris 集成（{@code onMatrixWrite} 里有
 * {@code isRenderingShadowPass} 之类的分支），不依赖这条旗标链路。
 *
 * <h2>修法</h2>
 * <ol>
 *   <li>镜内那一遍写窄投影<b>之前</b>，把 DH 的旗标重新立起来 ——
 *       DH 就会把窄投影捕获进自己的渲染状态，LOD 跟着倍率走；</li>
 *   <li>镜内那一遍结束、相机投影还原<b>之后</b>，再立一次旗标并用主投影
 *       <b>重写一次缓冲</b> —— DH 重新捕获宽 FOV，主画面的 LOD 不受污染。</li>
 * </ol>
 *
 * <p>全部走反射：本仓库不编译依赖 DH，DH 不在时静默返回 false。</p>
 */
public final class DistantHorizonsCompat {

    /** DH 的 mod id（fabric.mod.json 实读）。 */
    private static final String MOD_ID = "distanthorizons";

    /** {@code MixinProjectionMatrixBufferCommon.getNewMvmMatrix}，public static boolean。 */
    private static final String FLAG_CLASS = "com.seibel.distanthorizons.common.commonMixins.MixinProjectionMatrixBufferCommon";
    private static final String FLAG_FIELD = "getNewMvmMatrix";

    private static boolean resolved;
    private static Field newMatrixFlag;
    private static boolean logged;

    private DistantHorizonsCompat() {
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            if (Platform.INSTANCE.isModLoaded(MOD_ID)) {
                newMatrixFlag = Class.forName(FLAG_CLASS).getField(FLAG_FIELD);
            }
        } catch (Throwable t) {
            newMatrixFlag = null;
            GunMod.LOGGER.warn("[TACZ Scope] Distant Horizons projection bridge unavailable; "
                    + "distant LOD terrain may not follow the scope zoom.", t);
        }
    }

    /**
     * 重新立起 DH 的「接受下一次投影写入」旗标。
     *
     * @return true 表示 DH 在场且旗标立成功（调用方据此决定要不要重写缓冲）
     */
    public static boolean armCapture() {
        resolve();
        if (newMatrixFlag == null) {
            return false;
        }
        try {
            newMatrixFlag.setBoolean(null, true);
            if (!logged) {
                logged = true;
                GunMod.LOGGER.info("[TACZ Scope] Distant Horizons projection capture re-armed for the scope pass.");
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
