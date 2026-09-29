package com.tacz.guns.forge.platform;

import com.tacz.guns.forge.network.ForgeNetworking;
import com.tacz.guns.platform.NetworkPlatform;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;

public final class ForgeNetworkPlatform implements NetworkPlatform {
    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ForgeNetworking.channel().send(payload, PacketDistributor.SERVER.noArg());
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ForgeNetworking.channel().send(payload, PacketDistributor.PLAYER.with(player));
    }

    @Override
    public void sendToTrackingEntity(Entity entity, CustomPacketPayload payload) {
        ForgeNetworking.channel().send(payload, PacketDistributor.TRACKING_ENTITY.with(entity));
    }

    @Override
    public void sendToTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload) {
        ForgeNetworking.channel().send(payload, PacketDistributor.TRACKING_ENTITY_AND_SELF.with(entity));
    }

    @Override
    public void sendToAllPlayers(MinecraftServer server, CustomPacketPayload payload) {
        server.getPlayerList().broadcastAll(toClientboundPacket(payload));
    }

    @Override
    public void sendToDimension(ServerLevel level, CustomPacketPayload payload) {
        ForgeNetworking.channel().send(payload, PacketDistributor.DIMENSION.with(level.dimension()));
    }

    @Override
    public boolean supportsBundledPayloads() {
        return true;
    }

    @Override
    public Packet<? super ClientGamePacketListener> toClientboundPacket(CustomPacketPayload payload) {
        return NetworkDirection.PLAY_TO_CLIENT.buildPacket(ForgeNetworking.channel(), payload);
    }
}
