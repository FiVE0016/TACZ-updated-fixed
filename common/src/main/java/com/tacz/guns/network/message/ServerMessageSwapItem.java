package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.event.SwapItemWithOffHand;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public class ServerMessageSwapItem implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_swap_item");
    public static final CustomPacketPayload.Type<ServerMessageSwapItem> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ServerMessageSwapItem> CODEC = StreamCodec.ofMember(ServerMessageSwapItem::write, ServerMessageSwapItem::new);

    public ServerMessageSwapItem() {

    }

    public ServerMessageSwapItem(FriendlyByteBuf buf) {
        this();
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

        public static void handle(ServerMessageSwapItem message) {
            SwapItemWithOffHand.CALLBACK.invoker().post(new SwapItemWithOffHand());
        }
    }
}
