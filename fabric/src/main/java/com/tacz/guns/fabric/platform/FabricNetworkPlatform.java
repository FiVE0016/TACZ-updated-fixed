package com.tacz.guns.fabric.platform;

import com.tacz.guns.platform.NetworkPlatform;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class FabricNetworkPlatform implements NetworkPlatform {
    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToTrackingEntity(Entity entity, CustomPacketPayload payload) {
        for (ServerPlayer player : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    @Override
    public void sendToTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload) {
        if (entity instanceof ServerPlayer player) {
            ServerPlayNetworking.send(player, payload);
        }
        sendToTrackingEntity(entity, payload);
    }

    @Override
    public void sendToAllPlayers(MinecraftServer server, CustomPacketPayload payload) {
        for (ServerPlayer player : PlayerLookup.all(server)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    @Override
    public void sendToDimension(ServerLevel level, CustomPacketPayload payload) {
        for (ServerPlayer player : PlayerLookup.level(level)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    @Override
    public boolean supportsBundledPayloads() {
        return true;
    }

    @Override
    public Packet<? super ClientGamePacketListener> toClientboundPacket(CustomPacketPayload payload) {
        return ServerPlayNetworking.createClientboundPacket(payload);
    }
}
