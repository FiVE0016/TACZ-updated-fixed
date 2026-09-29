package com.tacz.guns.platform;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/**
 * A reload listener with a stable id, which Fabric's resource loader requires and which the
 * other loaders use for logging and ordering.
 */
public interface IdentifiableReloadListener extends PreparableReloadListener {
    Identifier getReloadListenerId();
}
