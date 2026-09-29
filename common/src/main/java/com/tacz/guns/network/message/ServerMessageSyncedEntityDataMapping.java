package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.entity.sync.core.SyncedDataKey;
import com.tacz.guns.entity.sync.core.SyncedEntityData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tells a joining client which network id the server uses for each synced entity data key.
 * <p>
 * The Forge original sent this during login; it is now the first TACZ packet of the play phase,
 * sent before any {@link ServerMessageUpdateEntityData}, so it works the same way on every loader.
 */
public class ServerMessageSyncedEntityDataMapping implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_synced_entity_data_mapping");
    public static final CustomPacketPayload.Type<ServerMessageSyncedEntityDataMapping> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ServerMessageSyncedEntityDataMapping> CODEC = StreamCodec.ofMember(ServerMessageSyncedEntityDataMapping::write, ServerMessageSyncedEntityDataMapping::new);

    private static final Marker MARKER = MarkerFactory.getMarker("TACZ_SYNCED_DATA_MAPPING");

    private final Map<Identifier, List<Pair<Identifier, Integer>>> keyMap;

    /**
     * Captures the server's current key mapping.
     */
    public ServerMessageSyncedEntityDataMapping() {
        this.keyMap = new HashMap<>();
        SyncedEntityData data = SyncedEntityData.instance();
        for (SyncedDataKey<?, ?> key : data.getKeys()) {
            this.keyMap.computeIfAbsent(key.classKey().id(), k -> new ArrayList<>()).add(Pair.of(key.id(), data.getInternalId(key)));
        }
    }

    public ServerMessageSyncedEntityDataMapping(FriendlyByteBuf buf) {
        int size = buf.readInt();
        this.keyMap = new HashMap<>();
        for (int i = 0; i < size; ++i) {
            Identifier classId = buf.readIdentifier();
            Identifier keyId = buf.readIdentifier();
            int id = buf.readVarInt();
            this.keyMap.computeIfAbsent(classId, k -> new ArrayList<>()).add(Pair.of(keyId, id));
        }
    }

    public void write(FriendlyByteBuf buf) {
        int size = this.keyMap.values().stream().mapToInt(List::size).sum();
        buf.writeInt(size);
        this.keyMap.forEach((classId, keys) -> keys.forEach(pair -> {
            buf.writeIdentifier(classId);
            buf.writeIdentifier(pair.getLeft());
            buf.writeVarInt(pair.getRight());
        }));
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

        public static void handle(ServerMessageSyncedEntityDataMapping message) {
            GunMod.LOGGER.debug(MARKER, "Received synced key mappings from server");
            if (!SyncedEntityData.instance().updateMappings(message.keyMap)) {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    player.connection.getConnection().disconnect(Component.literal("Connection closed - [TacZ] Received unknown synced data keys."));
                }
            }
        }
    }
}
