package com.tacz.guns.neoforge.client;

import com.tacz.guns.client.gui.compat.FallbackConfigScreen;
import com.tacz.guns.compat.cloth.MenuIntegration;
import com.tacz.guns.init.CompatRegistry;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * The mod list's config button. Backed by the Cloth Config screen when Cloth Config is installed,
 * and by TACZ's own small screen when it is not, so the scope/PIP toggles stay reachable in game.
 */
final class ClothConfigScreenFactory implements IConfigScreenFactory {
    @Override
    public Screen createScreen(ModContainer container, Screen modListScreen) {
        if (ModList.get().isLoaded(CompatRegistry.CLOTH_CONFIG)) {
            return MenuIntegration.getConfigBuilder().setParentScreen(modListScreen).build();
        }
        return new FallbackConfigScreen(modListScreen);
    }
}
