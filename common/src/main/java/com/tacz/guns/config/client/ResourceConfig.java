package com.tacz.guns.config.client;
import com.tacz.guns.config.spec.TaczConfigSpec;


public class ResourceConfig {
    public static TaczConfigSpec.BooleanValue ENABLE_LAZY_CLIENT_ASSET_LOAD;

    public static void init(TaczConfigSpec.Builder builder) {
        builder.push("resource");

        builder.comment("Build heavy TACZ client assets such as models and animation state machines on demand.",
                "Inventory items are pre-warmed in the background when possible.",
                "If a render needs an asset before warmup finishes, the render thread will wait for it once.");
        ENABLE_LAZY_CLIENT_ASSET_LOAD = builder.define("EnableLazyClientAssetLoad", true);

        builder.pop();
    }
}
