package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.Objects;

public class ServerMessageSyncBaseTimestamp implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_sync_base_timestamp");
    public static final CustomPacketPayload.Type<ServerMessageSyncBaseTimestamp> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ServerMessageSyncBaseTimestamp> CODEC = StreamCodec.ofMember(ServerMessageSyncBaseTimestamp::write, ServerMessageSyncBaseTimestamp::new);

    private static final Marker MARKER = MarkerFactory.getMarker("SYNC_BASE_TIMESTAMP");

    public ServerMessageSyncBaseTimestamp(FriendlyByteBuf buf) {
        this();
    }

    public ServerMessageSyncBaseTimestamp() {

    }

        public void write(FriendlyByteBuf buf) {

    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Client-side handling. Kept in its own class so a dedicated server never loads or verifies client code.
     */
    public static final class Client {
        private Client() {
        }

        public static void handle(ServerMessageSyncBaseTimestamp message) {
            long timestamp = System.currentTimeMillis();
            updateBaseTimestamp(timestamp);
            NetworkHandler.sendToServer(new ClientMessageSyncBaseTimestamp());
        }

        private static void updateBaseTimestamp(long timestamp) {
            LocalPlayer player = Objects.requireNonNull(Minecraft.getInstance().player);
            LocalPlayerDataHolder dataHolder = IClientPlayerGunOperator.fromLocalPlayer(player).getDataHolder();
            dataHolder.clientBaseTimestamp = timestamp;
            GunMod.LOGGER.debug(MARKER, "Update client base timestamp: {}", dataHolder.clientBaseTimestamp);
        }
    }
}
