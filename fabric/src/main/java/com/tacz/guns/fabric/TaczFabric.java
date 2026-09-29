package com.tacz.guns.fabric;

import com.tacz.guns.GunMod;
import com.tacz.guns.init.TaczCommonEvents;
import com.tacz.guns.init.TaczRegistration;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.platform.NetworkPlatform;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionResult;

/**
 * Fabric (and Quilt) entrypoint: registers everything immediately and wires TACZ's hooks to Fabric API events.
 */
public final class TaczFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        GunMod.init();
        TaczRegistration.registerAll();
        NetworkHandler.payloads().forEach(TaczFabric::registerPayload);

        ServerLifecycleEvents.SERVER_STARTING.register(TaczCommonEvents::onServerStarting);
        ServerLifecycleEvents.SERVER_STARTED.register(TaczCommonEvents::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(TaczCommonEvents::onServerStopped);
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register(TaczCommonEvents::onDatapackSync);
        CommonLifecycleEvents.TAGS_LOADED.register(TaczCommonEvents::onTagsLoaded);

        ServerTickEvents.START_SERVER_TICK.register(TaczCommonEvents::onServerTickStart);
        ServerTickEvents.END_SERVER_TICK.register(TaczCommonEvents::onServerTickEnd);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> TaczCommonEvents.onPlayerLoggedIn(handler.getPlayer()));
        ServerPlayerEvents.COPY_FROM.register(TaczCommonEvents::onPlayerClone);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> TaczCommonEvents.onPlayerRespawn(newPlayer, alive));
        EntityTrackingEvents.START_TRACKING.register(TaczCommonEvents::onStartTracking);
        // Non-player entities are copied into the new level; players are moved and need their own event.
        ServerEntityLevelChangeEvents.AFTER_ENTITY_CHANGE_LEVEL.register(TaczCommonEvents::onEntityChangeDimension);
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(TaczCommonEvents::onPlayerChangeDimension);

        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                TaczCommonEvents.shouldCancelLeftClickBlock(player, level, hand, pos, direction) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) ->
                TaczCommonEvents.shouldCancelAttackEntity(player, level, hand, entity) ? InteractionResult.FAIL : InteractionResult.PASS);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> TaczCommonEvents.registerCommands(dispatcher));
    }

    private static <T extends CustomPacketPayload> void registerPayload(NetworkPlatform.PayloadSpec<T> spec) {
        if (spec.direction() == NetworkPlatform.Direction.SERVERBOUND) {
            PayloadTypeRegistry.serverboundPlay().register(spec.type(), spec.codec());
            ServerPlayNetworking.registerGlobalReceiver(spec.type(), (payload, context) -> spec.handler().handle(payload, context.player()));
        } else {
            // Receivers for clientbound payloads are registered by TaczFabricClient.
            PayloadTypeRegistry.clientboundPlay().register(spec.type(), spec.codec());
        }
    }
}
