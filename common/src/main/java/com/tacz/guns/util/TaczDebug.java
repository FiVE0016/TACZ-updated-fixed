package com.tacz.guns.util;

import com.tacz.guns.GunMod;

/**
 * 临时诊断开关。
 *
 * <p><b>为什么存在</b>：趴下松键、榴弹出生点这两个问题在纯静态分析下推不出结论 ——
 * 需要一次真实复现把「按键事件有没有到 / 偏移算出来没有 / 服务端接没接到 / 校验过没过」
 * 这几步串起来。默认开启，问题定位后删掉即可（所有调用点都带 {@code [TACZDBG]} 前缀，
 * 便于 grep）。</p>
 *
 * <p>关掉的办法：加启动参数 {@code -Dtacz.debug=false}。</p>
 */
public final class TaczDebug {
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("tacz.debug", "true"));

    private TaczDebug() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static void log(String format, Object... args) {
        if (ENABLED) {
            GunMod.LOGGER.info("[TACZDBG] " + format, args);
        }
    }
}
