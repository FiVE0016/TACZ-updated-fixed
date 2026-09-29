package com.tacz.guns.network;

import cn.sh1rocu.tacz.api.extension.IEntityAdditionalSpawnData;
import com.tacz.guns.network.message.*;
import com.tacz.guns.network.message.event.*;
import com.tacz.guns.platform.NetworkPlatform;
import com.tacz.guns.platform.NetworkPlatform.Direction;
import com.tacz.guns.platform.NetworkPlatform.PayloadHandler;
import com.tacz.guns.platform.NetworkPlatform.PayloadSpec;
import io.netty.buffer.Unpooled;
import me.xjqsh.lrtactical.network.ClientMessagePrepareMeleeAttack;
import me.xjqsh.lrtactical.network.ServerMessageCustomCooldown;
import me.xjqsh.lrtactical.network.ServerMessageSyncLrPack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.function.Consumer;

/**
 * Declares every TACZ play payload and its handler once. Each loader registers {@link #payloads()}
 * with its own networking API; sending goes through {@link NetworkPlatform}.
 * <p>
 * Dedicated servers load this class and every payload class, so clientbound handlers must only call into
 * client-only classes (the payloads' nested {@code Client} classes) from lambdas: no client types may appear
 * in code that the server loads or verifies.
 */
public class NetworkHandler {
    private static final List<PayloadSpec<?>> PAYLOADS = List.of(
            // Client -> server
            serverbound(ClientMessagePlayerShoot.TYPE, ClientMessagePlayerShoot.CODEC, ClientMessagePlayerShoot::handle),
            serverbound(ClientMessagePlayerReloadGun.TYPE, ClientMessagePlayerReloadGun.CODEC, ClientMessagePlayerReloadGun::handle),
            serverbound(ClientMessagePlayerCancelReload.TYPE, ClientMessagePlayerCancelReload.CODEC, ClientMessagePlayerCancelReload::handle),
            serverbound(ClientMessagePlayerFireSelect.TYPE, ClientMessagePlayerFireSelect.CODEC, ClientMessagePlayerFireSelect::handle),
            serverbound(ClientMessagePlayerAim.TYPE, ClientMessagePlayerAim.CODEC, ClientMessagePlayerAim::handle),
            serverbound(ClientMessagePlayerCrawl.TYPE, ClientMessagePlayerCrawl.CODEC, ClientMessagePlayerCrawl::handle),
            serverbound(ClientMessagePlayerDrawGun.TYPE, ClientMessagePlayerDrawGun.CODEC, ClientMessagePlayerDrawGun::handle),
            serverbound(ClientMessageCraft.TYPE, ClientMessageCraft.CODEC, ClientMessageCraft::handle),
            serverbound(ClientMessagePlayerZoom.TYPE, ClientMessagePlayerZoom.CODEC, ClientMessagePlayerZoom::handle),
            serverbound(ClientMessageRefitGun.TYPE, ClientMessageRefitGun.CODEC, ClientMessageRefitGun::handle),
            serverbound(ClientMessageUnloadAttachment.TYPE, ClientMessageUnloadAttachment.CODEC, ClientMessageUnloadAttachment::handle),
            serverbound(ClientMessagePlayerBoltGun.TYPE, ClientMessagePlayerBoltGun.CODEC, ClientMessagePlayerBoltGun::handle),
            serverbound(ClientMessagePlayerMelee.TYPE, ClientMessagePlayerMelee.CODEC, ClientMessagePlayerMelee::handle),
            serverbound(ClientMessageSyncBaseTimestamp.TYPE, ClientMessageSyncBaseTimestamp.CODEC, ClientMessageSyncBaseTimestamp::handle),
            serverbound(ClientMessageLaserColor.TYPE, ClientMessageLaserColor.CODEC, ClientMessageLaserColor::handle),

            // Server -> client
            clientbound(ServerMessageSyncedEntityDataMapping.TYPE, ServerMessageSyncedEntityDataMapping.CODEC, message -> ServerMessageSyncedEntityDataMapping.Client.handle(message)),
            clientbound(ServerMessageSyncServerConfig.TYPE, ServerMessageSyncServerConfig.CODEC, message -> ServerMessageSyncServerConfig.Client.handle(message)),
            clientbound(IEntityAdditionalSpawnData.EXTRA_DATA_TYPE, IEntityAdditionalSpawnData.EXTRA_DATA_CODEC, payload -> Client.handleExtraSpawnData(payload)),
            clientbound(ServerMessageSound.TYPE, ServerMessageSound.CODEC, message -> ServerMessageSound.Client.handle(message)),
            clientbound(ServerMessageCraft.TYPE, ServerMessageCraft.CODEC, message -> ServerMessageCraft.Client.handle(message)),
            clientbound(ServerMessageRefreshRefitScreen.TYPE, ServerMessageRefreshRefitScreen.CODEC, message -> ServerMessageRefreshRefitScreen.Client.handle(message)),
            clientbound(ServerMessageSwapItem.TYPE, ServerMessageSwapItem.CODEC, message -> ServerMessageSwapItem.Client.handle(message)),
            clientbound(ServerMessageLevelUp.TYPE, ServerMessageLevelUp.CODEC, message -> ServerMessageLevelUp.Client.handle(message)),
            clientbound(ServerMessageGunHurt.TYPE, ServerMessageGunHurt.CODEC, message -> ServerMessageGunHurt.Client.handle(message)),
            clientbound(ServerMessageGunKill.TYPE, ServerMessageGunKill.CODEC, message -> ServerMessageGunKill.Client.handle(message)),
            clientbound(ServerMessageUpdateEntityData.TYPE, ServerMessageUpdateEntityData.CODEC, message -> ServerMessageUpdateEntityData.Client.handle(message)),
            clientbound(ServerMessageSyncGunPack.TYPE, ServerMessageSyncGunPack.CODEC, message -> ServerMessageSyncGunPack.Client.handle(message)),
            clientbound(ServerMessageGunDraw.TYPE, ServerMessageGunDraw.CODEC, message -> ServerMessageGunDraw.Client.handle(message)),
            clientbound(ServerMessageGunFire.TYPE, ServerMessageGunFire.CODEC, message -> ServerMessageGunFire.Client.handle(message)),
            clientbound(ServerMessageGunFireSelect.TYPE, ServerMessageGunFireSelect.CODEC, message -> ServerMessageGunFireSelect.Client.handle(message)),
            clientbound(ServerMessageGunMelee.TYPE, ServerMessageGunMelee.CODEC, message -> ServerMessageGunMelee.Client.handle(message)),
            clientbound(ServerMessageGunReload.TYPE, ServerMessageGunReload.CODEC, message -> ServerMessageGunReload.Client.handle(message)),
            clientbound(ServerMessageGunShoot.TYPE, ServerMessageGunShoot.CODEC, message -> ServerMessageGunShoot.Client.handle(message)),
            clientbound(ServerMessageSyncBaseTimestamp.TYPE, ServerMessageSyncBaseTimestamp.CODEC, message -> ServerMessageSyncBaseTimestamp.Client.handle(message)),

            // LRTactical: client -> server
            serverbound(ClientMessagePrepareMeleeAttack.TYPE, ClientMessagePrepareMeleeAttack.CODEC, ClientMessagePrepareMeleeAttack::handle),
            // LRTactical: server -> client. The handlers sit in nested Client classes that dedicated servers never load.
            clientbound(ServerMessageSyncLrPack.TYPE, ServerMessageSyncLrPack.CODEC, message -> ServerMessageSyncLrPack.Client.handle(message)),
            clientbound(ServerMessageCustomCooldown.TYPE, ServerMessageCustomCooldown.CODEC, message -> ServerMessageCustomCooldown.Client.handle(message))
    );

    /**
     * @return every payload TACZ sends, with its codec, direction and handler
     */
    public static List<PayloadSpec<?>> payloads() {
        return PAYLOADS;
    }

    private static <T extends CustomPacketPayload> PayloadSpec<T> serverbound(CustomPacketPayload.Type<T> type,
                                                                            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                                            PayloadHandler<T> handler) {
        return new PayloadSpec<>(type, codec, Direction.SERVERBOUND, handler);
    }

    private static <T extends CustomPacketPayload> PayloadSpec<T> clientbound(CustomPacketPayload.Type<T> type,
                                                                            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                                            Consumer<T> handler) {
        return new PayloadSpec<>(type, codec, Direction.CLIENTBOUND, (payload, player) -> handler.accept(payload));
    }

    public static void sendToServer(CustomPacketPayload message) {
        NetworkPlatform.INSTANCE.sendToServer(message);
    }

    public static void sendToClientPlayer(CustomPacketPayload message, ServerPlayer player) {
        NetworkPlatform.INSTANCE.sendToPlayer(player, message);
    }

    /**
     * 发送给所有监听此实体的玩家
     */
    public static void sendToTrackingEntityAndSelf(Entity centerEntity, CustomPacketPayload message) {
        NetworkPlatform.INSTANCE.sendToTrackingEntityAndSelf(centerEntity, message);
    }

    public static void sendToAllPlayers(CustomPacketPayload message, MinecraftServer server) {
        NetworkPlatform.INSTANCE.sendToAllPlayers(server, message);
    }

    public static void sendToTrackingEntity(CustomPacketPayload message, final Entity centerEntity) {
        NetworkPlatform.INSTANCE.sendToTrackingEntity(centerEntity, message);
    }

    public static void sendToDimension(CustomPacketPayload message, final Entity centerEntity) {
        if (centerEntity.level() instanceof ServerLevel serverLevel) {
            NetworkPlatform.INSTANCE.sendToDimension(serverLevel, message);
        }
    }

    /**
     * Client-side handling for payloads that have no class of their own.
     */
    private static final class Client {
        private Client() {
        }

        static void handleExtraSpawnData(IEntityAdditionalSpawnData.ExtraSpawnDataPayload payload) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null && level.getEntity(payload.entityId()) instanceof IEntityAdditionalSpawnData extra) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
                extra.readSpawnData(buf);
                buf.release();
            }
        }
    }
}
