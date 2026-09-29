package com.tacz.guns.config;

import cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig;
import com.tacz.guns.config.client.*;
import com.tacz.guns.config.spec.TaczConfigSpec;

public class ClientConfig {
    public static TaczConfigSpec init() {
        TaczConfigSpec.Builder builder = new TaczConfigSpec.Builder();
        KeyConfig.init(builder);
        RenderConfig.init(builder);
        ResourceConfig.init(builder);
        SoundConfig.init(builder);
        ZoomConfig.init(builder);
        MeshyConfig.init(builder);
        return builder.build();
    }
}
