package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public class ClientMessagePlayerShoot implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "c2s_player_shoot");
    public static final CustomPacketPayload.Type<ClientMessagePlayerShoot> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ClientMessagePlayerShoot> CODEC = StreamCodec.ofMember(ClientMessagePlayerShoot::write, ClientMessagePlayerShoot::new);

    /**
     * 这里的 timestamp 应该是基于 base timestamp 的相对值
     */
    private final long timestamp;
    private float chargeProgress;
    /**
     * 客户端算出的【枪口相对眼睛的世界轴偏移】，可为 null。
     *
     * <p>服务端拿它决定弹丸的出生点（见 {@code ModernKineticGunScriptAPI#resolveMuzzleOrigin}）。
     * 第三人称、模型里没有 {@code muzzle_flash} 节点、或旧版客户端时为 null，
     * 此时服务端按原行为在眼睛处生成。</p>
     */
    @Nullable
    private final org.joml.Vector3f muzzleOffset;

    public ClientMessagePlayerShoot(long timestamp) {
        this(timestamp, 0f);
    }

    public ClientMessagePlayerShoot(long timestamp, float chargeProgress) {
        this(timestamp, chargeProgress, null);
    }

    public ClientMessagePlayerShoot(long timestamp, float chargeProgress, @Nullable org.joml.Vector3f muzzleOffset) {
        this.timestamp = timestamp;
        this.chargeProgress = chargeProgress;
        this.muzzleOffset = muzzleOffset;
    }

    public ClientMessagePlayerShoot(FriendlyByteBuf buf) {
        this.timestamp = buf.readLong();
        this.chargeProgress = buf.readFloat();
        this.muzzleOffset = buf.readBoolean()
                ? new org.joml.Vector3f(buf.readFloat(), buf.readFloat(), buf.readFloat())
                : null;
    }

        public void write(FriendlyByteBuf buf) {
        buf.writeLong(timestamp);
        buf.writeFloat(chargeProgress);
        buf.writeBoolean(muzzleOffset != null);
        if (muzzleOffset != null) {
            buf.writeFloat(muzzleOffset.x());
            buf.writeFloat(muzzleOffset.y());
            buf.writeFloat(muzzleOffset.z());
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player) {
        com.tacz.guns.util.TaczDebug.log("服务端收到开火包: muzzleOffset={}", muzzleOffset == null
                ? "null"
                : String.format("(%.3f, %.3f, %.3f)", muzzleOffset.x(), muzzleOffset.y(), muzzleOffset.z()));
        IGunOperator.fromLivingEntity(player).shoot(player::getXRot, player::getYRot, timestamp, chargeProgress, muzzleOffset);
    }
}
