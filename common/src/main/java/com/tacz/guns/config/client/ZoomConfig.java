package com.tacz.guns.config.client;
import com.tacz.guns.config.spec.TaczConfigSpec;


public class ZoomConfig {
    public static TaczConfigSpec.DoubleValue SCREEN_DISTANCE_COEFFICIENT;
    public static TaczConfigSpec.DoubleValue ZOOM_SENSITIVITY_BASE_MULTIPLIER;

    public static void init(TaczConfigSpec.Builder builder) {
        builder.push("Zoom");

        builder.comment("Screen distance coefficient for zoom, using MDV standard, default is MDV133");
        SCREEN_DISTANCE_COEFFICIENT = builder.defineInRange("ScreenDistanceCoefficient", 1.33D, 0.0D, 3.0D);

        builder.comment("Zoom sensitivity is multiplied by this factor");
        ZOOM_SENSITIVITY_BASE_MULTIPLIER = builder.defineInRange("ZoomSensitivityBaseMultiplier", 1.0D, 0.0D, 2.0D);

        builder.pop();
    }
}
