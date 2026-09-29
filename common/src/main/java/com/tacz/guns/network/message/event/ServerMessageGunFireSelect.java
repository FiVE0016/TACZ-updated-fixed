package com.tacz.guns.network.message.event;

import cn.sh1rocu.tacz.api.LogicalSide;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.event.common.GunFireSelectEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class ServerMessageGunFireSelect implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_gunfire_select");
    public static final CustomPacketPayload.Type<ServerMessageGunFireSelect> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerMessageGunFireSelect> CODEC = StreamCodec.ofMember(ServerMessageGunFireSelect::write, ServerMessageGunFireSelect::new);

    private final int shooterId;
    private final ItemStack gunItemStack;

    public ServerMessageGunFireSelect(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(), ItemStack.STREAM_CODEC.decode(buf));
    }

    public ServerMessageGunFireSelect(int shooterId, ItemStack gunItemStack) {
        this.shooterId = shooterId;
        this.gunItemStack = gunItemStack;
    }

        public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(shooterId);
        ItemStack.STREAM_CODEC.encode(buf, gunItemStack);
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

        public static void handle(ServerMessageGunFireSelect message) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            if (level.getEntity(message.shooterId) instanceof LivingEntity shooter) {
                GunFireSelectEvent gunFireSelectEvent = new GunFireSelectEvent(shooter, message.gunItemStack, LogicalSide.CLIENT);
                GunFireSelectEvent.CALLBACK.invoker().post(gunFireSelectEvent);
            }
        }
    }
}
