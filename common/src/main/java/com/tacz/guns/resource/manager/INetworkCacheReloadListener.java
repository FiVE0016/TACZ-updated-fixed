package com.tacz.guns.resource.manager;
import com.tacz.guns.platform.IdentifiableReloadListener;

import com.tacz.guns.resource.network.DataType;
import net.minecraft.resources.Identifier;

import java.util.Map;

public interface INetworkCacheReloadListener extends IdentifiableReloadListener {
    Map<Identifier, String> getNetworkCache();

    DataType getType();
}
