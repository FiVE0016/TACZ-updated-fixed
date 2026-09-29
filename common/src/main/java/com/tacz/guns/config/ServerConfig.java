package com.tacz.guns.config;
import com.tacz.guns.config.spec.TaczConfigSpec;

import com.tacz.guns.config.sync.SyncConfig;

public class ServerConfig {
    /**
     * 因为 Forge 配置文件的加载时间窗口问题，导致有些地方会提前调用配置文件，故缓存一下检查是否已经加载了
     */
    public static TaczConfigSpec SERVER_CONFIG_SPEC;

    public static TaczConfigSpec init() {
        TaczConfigSpec.Builder builder = new TaczConfigSpec.Builder();
        SyncConfig.init(builder);
        SERVER_CONFIG_SPEC = builder.build();
        return SERVER_CONFIG_SPEC;
    }
}
