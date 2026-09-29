package com.tacz.guns.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.tacz.guns.init.TaczCommonEvents;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds TACZ's server data reload listeners, which need the {@link ReloadableServerResources} being built.
 * Fabric has no event for this; Forge and NeoForge use their reload listener events instead, which is
 * also why this mixin (targeting a synthetic lambda of vanilla's code) only ships with the Fabric build.
 */
@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesMixin {
    @ModifyArg(
            method = "lambda$loadResources$2",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/SimpleReloadInstance;create(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Z)Lnet/minecraft/server/packs/resources/ReloadInstance;"),
            index = 1
    )
    private static List<PreparableReloadListener> tacz$addReloadListener(List<PreparableReloadListener> original,
                                                                         @Local(ordinal = 0) ReloadableServerResources serverResources) {
        ArrayList<PreparableReloadListener> listeners = new ArrayList<>(original);
        TaczCommonEvents.registerServerReloadListeners(serverResources, listeners::add);
        return listeners;
    }
}
