package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public class ServerMessageCraft implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_craft");
    public static final CustomPacketPayload.Type<ServerMessageCraft> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ServerMessageCraft> CODEC = StreamCodec.ofMember(ServerMessageCraft::write, ServerMessageCraft::new);

    private final int menuId;

    public ServerMessageCraft(int menuId) {
        this.menuId = menuId;
    }


    public ServerMessageCraft(FriendlyByteBuf buf) {
        this(buf.readVarInt());
    }

        public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(menuId);
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

        public static void handle(ServerMessageCraft message) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && player.containerMenu.containerId == message.menuId && Minecraft.getInstance().gui.screen() instanceof GunSmithTableScreen screen) {
                screen.updateIngredientCount();
            }
        }
    }
}
