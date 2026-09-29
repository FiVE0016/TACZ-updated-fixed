package com.tacz.guns.compat.zoomify;

import cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent;
import com.tacz.guns.GunMod;
import com.tacz.guns.platform.Platform;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Applies Zoomify's zoom to the field of view. Zoomify only has Fabric/Quilt builds for 26.2 and its main
 * class implements Fabric's entrypoint interface, so it is reached reflectively instead of compiled against.
 */
public class ZoomifyCompat {
    private static final String MOD_ID = "zoomify";
    @Nullable
    private static MethodHandle zoomDivisor;

    public static void init() {
        if (!Platform.INSTANCE.isModLoaded(MOD_ID)) {
            return;
        }
        try {
            Method method = Class.forName("dev.isxander.zoomify.Zoomify").getMethod("getZoomDivisor", float.class);
            if (!Modifier.isStatic(method.getModifiers())) {
                throw new NoSuchMethodException("Zoomify.getZoomDivisor(float) is not static");
            }
            zoomDivisor = MethodHandles.publicLookup().unreflect(method)
                    .asType(MethodType.methodType(double.class, float.class));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            GunMod.LOGGER.error("Zoomify is installed, but its zoom divisor could not be found: {}", e.toString());
            return;
        }
        ViewportEvent.FOV.register(event -> event.setFOV(getFov(event.getFOV(), (float) event.getPartialTick())));
    }

    public static double getFov(double fov, float tickDelta) {
        MethodHandle divisor = zoomDivisor;
        if (divisor == null) {
            return fov;
        }
        try {
            return fov / (double) divisor.invokeExact(tickDelta);
        } catch (Throwable e) {
            GunMod.LOGGER.error("Error while getting Zoomify zoom divisor: " + e.getMessage());
            return fov;
        }
    }
}
