package com.tacz.guns.neoforge.compat.rei;

import cn.sh1rocu.tacz.compat.rei.REIClientPlugin;
import me.shedaniel.rei.forge.REIPluginClient;

/**
 * REI on NeoForge finds plugins by annotation instead of by entrypoint.
 */
@REIPluginClient
public final class TaczReiClientPlugin extends REIClientPlugin {
}
