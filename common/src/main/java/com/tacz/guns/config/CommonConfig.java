package com.tacz.guns.config;
import com.tacz.guns.config.spec.TaczConfigSpec;

import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.config.common.GunConfig;
import com.tacz.guns.config.common.OtherConfig;

public final class CommonConfig {
    public static TaczConfigSpec init() {
        TaczConfigSpec.Builder builder = new TaczConfigSpec.Builder();
        GunConfig.init(builder);
        AmmoConfig.init(builder);
        OtherConfig.init(builder);
        return builder.build();
    }
}
