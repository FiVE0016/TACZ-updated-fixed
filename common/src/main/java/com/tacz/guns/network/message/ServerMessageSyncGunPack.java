package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.compat.RecipeViewerReloadBridge;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.network.CommonNetworkCache;
import com.tacz.guns.resource.network.DataType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import com.tacz.guns.util.BufMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Map;

public class ServerMessageSyncGunPack implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_sync_gunpack");
    public static final CustomPacketPayload.Type<ServerMessageSyncGunPack> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ServerMessageSyncGunPack> CODEC = StreamCodec.ofMember(ServerMessageSyncGunPack::write, ServerMessageSyncGunPack::new);

    private final Map<DataType, Map<Identifier, String>> cache;

    public ServerMessageSyncGunPack(FriendlyByteBuf buf) {
        // Explicit lambdas: NeoForge adds readMap/writeMap overloads that make overloaded method references ambiguous.
        this(BufMap.read(buf, buf1 -> buf1.readEnum(DataType.class),
                buf2 -> BufMap.read(buf2, buf3 -> buf3.readIdentifier(), buf3 -> buf3.readUtf())));
    }

    public ServerMessageSyncGunPack(Map<DataType, Map<Identifier, String>> cache) {
        this.cache = cache;
    }

        public void write(FriendlyByteBuf buf) {
        BufMap.write(buf, getCache(), (buf1, type) -> buf1.writeEnum(type), (buf1, map) ->
                BufMap.write(buf1, map, (buf2, id) -> buf2.writeIdentifier(id), (buf2, value) -> buf2.writeUtf(value)));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public Map<DataType, Map<Identifier, String>> getCache() {
        return cache;
    }

    /**
     * Client-side handling. Kept in its own class so a dedicated server never loads or verifies client code.
     */
    public static final class Client {
        private Client() {
        }

        public static void handle(ServerMessageSyncGunPack message) {
            LocalPlayer player = Minecraft.getInstance().player;
            Connection connection = player == null ? null : player.connection.getConnection();
            // Network delivery need not be on the client event loop. Cache installation, index rebuilding,
            // and optional recipe-viewer registration all touch client-owned state, so keep their order
            // together on Minecraft's executor.
            Minecraft.getInstance().execute(() -> {
                boolean remoteConnection = connection != null && !connection.isMemoryConnection();
                doSync(message, remoteConnection);
            });
        }

        private static void doSync(ServerMessageSyncGunPack message, boolean remoteConnection) {
            if (remoteConnection) {
                CommonAssetsManager.clearInstance();
            }
            // Ordering is intentional: viewers must observe the newly installed cache and rebuilt index.
            CommonNetworkCache.INSTANCE.fromNetwork(message.cache);
            // 通知客户端重新构建ClientIndex
            ClientIndexManager.reload();
            RecipeViewerReloadBridge.requestReload();
        }
    }
}
