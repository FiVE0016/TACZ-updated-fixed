package me.xjqsh.lrtactical.network;

import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.init.ModCapabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Synchronizes LRTactical's id-keyed cooldown to the owning client. */
public record ServerMessageCustomCooldown(Identifier id, int duration) implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(
            EquipmentMod.MOD_ID, "s2c_custom_cooldown");
    public static final CustomPacketPayload.Type<ServerMessageCustomCooldown> TYPE =
            new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ServerMessageCustomCooldown> CODEC =
            StreamCodec.ofMember(ServerMessageCustomCooldown::write, ServerMessageCustomCooldown::new);

    public ServerMessageCustomCooldown(FriendlyByteBuf buffer) {
        this(buffer.readIdentifier(), buffer.readVarInt());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeIdentifier(id);
        buffer.writeVarInt(Math.max(0, duration));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Client-side handling, in a nested class that dedicated servers never load (they have no client classes).
     */
    public static final class Client {
        private Client() {
        }

        public static void handle(ServerMessageCustomCooldown message) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            var cooldowns = ModCapabilities.coolDowns(player);
            if (message.duration() <= 0) {
                cooldowns.removeCooldown(message.id());
            } else {
                cooldowns.addCooldown(message.id(), message.duration());
            }
        }
    }
}
