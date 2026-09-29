package me.xjqsh.lrtactical.network;

import com.tacz.guns.network.NetworkHandler;
import me.xjqsh.lrtactical.resource.CommonAssetsManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * LRTactical 的网络层：服务端发送。
 *
 * <p>The payloads ({@link ClientMessagePrepareMeleeAttack}, {@link ServerMessageSyncLrPack} and
 * {@link ServerMessageCustomCooldown}) are declared next to TACZ's in {@code NetworkHandler#payloads()}, which every
 * loader registers on both sides; this class only sends them.
 */
public final class LrNetworkHandler {
    private LrNetworkHandler() {
    }

    /**
     * 把当前索引发给某个玩家。
     *
     * <p>Called from {@code TaczCommonEvents#onDatapackSync}: when a player joins and after a data pack reload,
     * the same moments TACZ sends its own gun pack cache.
     */
    public static void syncToPlayer(ServerPlayer player, boolean joined) {
        NetworkHandler.sendToClientPlayer(new ServerMessageSyncLrPack(
                CommonAssetsManager.get().getThrowableIndexManager().getNetworkCache(),
                CommonAssetsManager.get().getMeleeIndexManager().getNetworkCache(),
                CommonAssetsManager.get().getConsumableIndexManager().getNetworkCache()), player);
    }

    public static void syncCooldown(ServerPlayer player, Identifier id, int duration) {
        NetworkHandler.sendToClientPlayer(new ServerMessageCustomCooldown(id, Math.max(0, duration)), player);
    }
}
