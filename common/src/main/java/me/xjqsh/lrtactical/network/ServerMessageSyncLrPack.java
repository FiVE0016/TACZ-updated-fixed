package me.xjqsh.lrtactical.network;

import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.resource.CommonAssetsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import com.tacz.guns.util.BufMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Map;

/** 把服务端加载的 LRTactical index 原始 JSON 同步给客户端。 */
public class ServerMessageSyncLrPack implements CustomPacketPayload {
    public static final Identifier PACKET_ID =
            Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "s2c_sync_lr_pack");
    public static final CustomPacketPayload.Type<ServerMessageSyncLrPack> TYPE =
            new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ServerMessageSyncLrPack> CODEC =
            StreamCodec.ofMember(ServerMessageSyncLrPack::write, ServerMessageSyncLrPack::new);

    private final Map<Identifier, String> throwableIndex;
    private final Map<Identifier, String> meleeIndex;
    private final Map<Identifier, String> consumableIndex;

    public ServerMessageSyncLrPack(Map<Identifier, String> throwableIndex,
                                   Map<Identifier, String> meleeIndex,
                                   Map<Identifier, String> consumableIndex) {
        this.throwableIndex = throwableIndex;
        this.meleeIndex = meleeIndex;
        this.consumableIndex = consumableIndex;
    }

    public ServerMessageSyncLrPack(FriendlyByteBuf buf) {
        this(readIndex(buf), readIndex(buf), readIndex(buf));
    }

    public void write(FriendlyByteBuf buf) {
        writeIndex(buf, this.throwableIndex);
        writeIndex(buf, this.meleeIndex);
        writeIndex(buf, this.consumableIndex);
    }

    // Explicit lambdas: NeoForge adds readMap/writeMap overloads that make overloaded method references ambiguous.
    private static Map<Identifier, String> readIndex(FriendlyByteBuf buf) {
        return BufMap.read(buf, buf1 -> buf1.readIdentifier(), buf1 -> buf1.readUtf());
    }

    private static void writeIndex(FriendlyByteBuf buf, Map<Identifier, String> index) {
        BufMap.write(buf, index, (buf1, id) -> buf1.writeIdentifier(id), (buf1, json) -> buf1.writeUtf(json));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Client-side handling. Dedicated servers load the message class but have no client classes, so the handler
     * lives in this nested class, which only the client ever loads.
     */
    public static final class Client {
        private Client() {
        }

        public static void handle(ServerMessageSyncLrPack message) {
            // In single player the integrated server already filled the shared managers; only a remote connection
            // needs the synced copy.
            ClientPacketListener listener = Minecraft.getInstance().getConnection();
            boolean remoteConnection = listener != null && !listener.getConnection().isMemoryConnection();
            if (!remoteConnection) {
                return;
            }
            CommonAssetsManager.get().getThrowableIndexManager().fromNetwork(message.throwableIndex);
            CommonAssetsManager.get().getMeleeIndexManager().fromNetwork(message.meleeIndex);
            CommonAssetsManager.get().getConsumableIndexManager().fromNetwork(message.consumableIndex);
        }
    }
}
