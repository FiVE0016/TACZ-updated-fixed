package com.tacz.guns.fabric.compat;

import com.tacz.guns.client.gui.compat.FallbackConfigScreen;
import com.tacz.guns.compat.cloth.MenuIntegration;
import com.tacz.guns.init.CompatRegistry;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Mod Menu's config button. Backed by the Cloth Config screen when Cloth Config is installed, and
 * by TACZ's own small screen when it is not, so the scope/PIP toggles stay reachable in game.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (FabricLoader.getInstance().isModLoaded(CompatRegistry.CLOTH_CONFIG)) {
            return parent -> MenuIntegration.getConfigBuilder().setParentScreen(parent).build();
        }
        return FallbackConfigScreen::new;
    }
}
