package com.tacz.guns;

import com.tacz.guns.api.resource.ResourceManager;
import com.tacz.guns.config.TaczConfigs;
import com.tacz.guns.init.CommonRegistry;
import com.tacz.guns.init.CompatRegistry;
import com.tacz.guns.init.TaczCommonEvents;
import com.tacz.guns.platform.Platform;
import com.tacz.guns.resource.GunPackLoader;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GunMod {
    public static final String MOD_ID = "tacz";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    /**
     * 默认模型包文件夹
     */
    public static final String DEFAULT_GUN_PACK_NAME = "tacz_default_gun";

    /**
     * Loader-independent setup, called once from each loader's entrypoint. Game objects are
     * registered separately through {@link com.tacz.guns.init.TaczRegistration}, inside each
     * loader's registration window.
     */
    public static void init() {
        TaczConfigs.init();
        GunPackLoader.INSTANCE.packType = Platform.INSTANCE.isPhysicalClient() ? PackType.CLIENT_RESOURCES : PackType.SERVER_DATA;

        CommonRegistry.onSetupEvent();
        TaczCommonEvents.registerListeners();
        CompatRegistry.onEnqueue();

        registerDefaultExtraGunPack();
        AttachmentPropertyManager.registerModifier();
    }

    private static void registerDefaultExtraGunPack() {
        String jarDefaultPackPath = String.format("/assets/%s/custom/%s", GunMod.MOD_ID, DEFAULT_GUN_PACK_NAME);
        ResourceManager.registerExportResource(GunMod.class, jarDefaultPackPath);
    }
}
