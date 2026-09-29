package com.tacz.guns.platform;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Sends TACZ's play-phase payloads. Payload types and handlers are declared once in
 * {@link com.tacz.guns.network.NetworkHandler}; each loader registers them with its own
 * networking API at the time that API requires. Handlers always run on the game thread.
 */
public interface NetworkPlatform {
    NetworkPlatform INSTANCE = Services.load(NetworkPlatform.class);

    void sendToServer(CustomPacketPayload payload);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /**
     * Sends to every player tracking the entity, excluding the entity itself.
     */
    void sendToTrackingEntity(Entity entity, CustomPacketPayload payload);

    /**
     * Sends to every player tracking the entity, and to the entity if it is a player.
     */
    void sendToTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload);

    void sendToAllPlayers(MinecraftServer server, CustomPacketPayload payload);

    void sendToDimension(ServerLevel level, CustomPacketPayload payload);

    /**
     * @return whether {@link #toClientboundPacket} works outside a specific connection, so a payload can be
     * bundled with a vanilla packet such as an entity spawn packet
     */
    boolean supportsBundledPayloads();

    /**
     * Wraps a registered clientbound payload in a vanilla packet. Only call when {@link #supportsBundledPayloads()}.
     */
    Packet<? super ClientGamePacketListener> toClientboundPacket(CustomPacketPayload payload);

    /**
     * A payload declaration shared by all loaders.
     */
    record PayloadSpec<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            Direction direction,
            PayloadHandler<T> handler) {
    }

    enum Direction {
        /**
         * Client to server.
         */
        SERVERBOUND,
        /**
         * Server to client.
         */
        CLIENTBOUND
    }

    @FunctionalInterface
    interface PayloadHandler<T extends CustomPacketPayload> {
        /**
         * @param player the sending player for serverbound payloads; {@code null} for clientbound payloads
         */
        void handle(T payload, ServerPlayer player);
    }
}
