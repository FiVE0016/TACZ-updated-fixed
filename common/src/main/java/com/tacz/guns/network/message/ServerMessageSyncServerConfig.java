package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.config.TaczConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sends the server's {@code tacz-server.toml} values to a client, which Forge used to do for every
 * server config automatically. Clients use the server's values while connected.
 */
public class ServerMessageSyncServerConfig implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_sync_server_config");
    public static final CustomPacketPayload.Type<ServerMessageSyncServerConfig> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ServerMessageSyncServerConfig> CODEC = StreamCodec.ofMember(ServerMessageSyncServerConfig::write, ServerMessageSyncServerConfig::new);

    private static final int MAX_LENGTH = 1 << 20;

    private final String toml;

    public ServerMessageSyncServerConfig(String toml) {
        this.toml = toml;
    }

    public ServerMessageSyncServerConfig(FriendlyByteBuf buf) {
        this(buf.readUtf(MAX_LENGTH));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(toml, MAX_LENGTH);
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

        public static void handle(ServerMessageSyncServerConfig message) {
            // An integrated server shares the very same config objects with this client.
            if (!Minecraft.getInstance().hasSingleplayerServer()) {
                TaczConfigs.applySyncedServerConfig(message.toml);
            }
        }
    }
}
