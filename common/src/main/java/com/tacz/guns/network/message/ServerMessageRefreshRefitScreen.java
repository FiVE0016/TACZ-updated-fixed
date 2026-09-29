package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public class ServerMessageRefreshRefitScreen implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_refresh_refit_screen");
    public static final CustomPacketPayload.Type<ServerMessageRefreshRefitScreen> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ServerMessageRefreshRefitScreen> CODEC = StreamCodec.ofMember(ServerMessageRefreshRefitScreen::write, ServerMessageRefreshRefitScreen::new);

    public ServerMessageRefreshRefitScreen() {

    }

    public ServerMessageRefreshRefitScreen(FriendlyByteBuf buf) {
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

        public static void handle(ServerMessageRefreshRefitScreen message) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && Minecraft.getInstance().gui.screen() instanceof GunRefitScreen screen) {
                screen.init();
                // 刷新配件数据，客户端的
                AttachmentPropertyManager.postChangeEvent(player, player.getMainHandItem());
            }
        }
    }
}
