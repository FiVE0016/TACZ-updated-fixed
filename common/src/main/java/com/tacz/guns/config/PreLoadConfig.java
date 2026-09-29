package com.tacz.guns.config;

import com.tacz.guns.config.spec.TaczConfigSpec;

import java.nio.file.Path;

/**
 * Settings that must be read before gun packs are discovered. They live beside the packs, in
 * {@code tacz/tacz-pre.toml}, rather than in the config folder.
 */
public class PreLoadConfig {
    public static final TaczConfigSpec spec;
    public static final TaczConfigSpec.BooleanValue override;

    static {
        TaczConfigSpec.Builder builder = new TaczConfigSpec.Builder();
        builder.push("gunpack");
        builder.comment("When enabled, the mod will not try to overwrite the default pack under .minecraft/tacz\n" +
                "Since 1.0.4, the overwriting will only run when you start client or a dedicated server");
        override = builder.define("DefaultPackDebug", false);
        builder.pop();
        spec = builder.build();
    }

    /**
     * @param configBasePath the gun pack folder that holds {@code tacz-pre.toml}
     */
    public static void load(Path configBasePath) {
        if (!spec.isLoaded()) {
            spec.load(configBasePath.resolve("tacz-pre.toml"));
        }
    }
}
