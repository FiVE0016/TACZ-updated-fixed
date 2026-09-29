package com.tacz.guns.config.client;
import com.tacz.guns.config.spec.TaczConfigSpec;


public class KeyConfig {
    public static TaczConfigSpec.BooleanValue HOLD_TO_AIM;
    public static TaczConfigSpec.BooleanValue HOLD_TO_CRAWL;
    public static TaczConfigSpec.BooleanValue AUTO_RELOAD;

    public static void init(TaczConfigSpec.Builder builder) {
        builder.push("key");

        builder.comment("True if you want to hold the right mouse button to aim");
        HOLD_TO_AIM = builder.define("HoldToAim", true);

        builder.comment("True if you want to hold the crawl button to crawl");
        HOLD_TO_CRAWL = builder.define("HoldToCrawl", true);

        builder.comment("Try to reload automatically when the gun is empty");
        AUTO_RELOAD = builder.define("AutoReload", false);

        builder.pop();
    }
}
