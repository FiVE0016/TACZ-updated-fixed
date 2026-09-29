package com.tacz.guns.compat.shouldersurfing;
import com.tacz.guns.platform.Platform;


/** Optional facade for Shoulder Surfing Reloaded 5.x on Minecraft 26.2. */
public final class ShoulderSurfingCompat {
    private static final String MOD_ID = "shouldersurfing";
    private static boolean INSTALLED = false;

    public static void init() {
        INSTALLED = Platform.INSTANCE.isModLoaded(MOD_ID);
    }

    public static boolean showCrosshair() {
        if (INSTALLED) {
            return ShoulderSurfingCompatInner.showCrosshair();
        }
        return false;
    }

    public static boolean isInstalled() {
        return INSTALLED;
    }
}
