package com.tacz.guns.neoforge.platform;

import com.tacz.guns.platform.NetworkPlatform;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NeoForgeNetworkPlatform implements NetworkPlatform {
    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToTrackingEntity(Entity entity, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
    }

    @Override
    public void sendToTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
    }

    @Override
    public void sendToAllPlayers(MinecraftServer server, CustomPacketPayload payload) {
        server.getPlayerList().broadcastAll(new ClientboundCustomPayloadPacket(payload));
    }

    @Override
    public void sendToDimension(ServerLevel level, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersInDimension(level, payload);
    }

    @Override
    public boolean supportsBundledPayloads() {
        return true;
    }

    @Override
    public Packet<? super ClientGamePacketListener> toClientboundPacket(CustomPacketPayload payload) {
        return new ClientboundCustomPayloadPacket(payload);
    }
}
