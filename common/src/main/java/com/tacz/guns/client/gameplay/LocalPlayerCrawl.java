package com.tacz.guns.client.gameplay;
import com.tacz.guns.mixin.accessor.LivingEntityAccessor;
import com.tacz.guns.network.NetworkHandler;

import cn.sh1rocu.tacz.api.mixin.ForcePoseInjection;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.network.message.ClientMessagePlayerCrawl;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

public class LocalPlayerCrawl {
    /**
     * 冷却时间为 10 tick
     */
    private static final int COOLDOWN_TICKS = 10;
    private final LocalPlayer player;
    private boolean isCrawling = false;
    private int crawCooldownTicks = 0;

    public LocalPlayerCrawl(LocalPlayer player) {
        this.player = player;
    }

    public void crawl(boolean isCrawl) {
        com.tacz.guns.util.TaczDebug.log("LocalPlayerCrawl.crawl({}) local={} cooldown={} onGround={} item={}",
                isCrawl, isCrawling, crawCooldownTicks, player.onGround(),
                player.getMainHandItem().getItem());
        // 起身走独立分支：不受「进入趴下」那套前置条件（持枪 / 冷却 / 着地）限制。
        // 旧实现把「松开按键」也塞进下面那串检查里，于是按住 C 快速点按时，松开事件
        // 会被 10 tick 冷却吞掉（趴下之后 onGround 也可能为 false），人就一直趴着起不来。
        if (!isCrawl) {
            this.cancelCrawl();
            return;
        }
        // 持枪才能按键趴下
        ItemStack mainHandItem = player.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof IGun iGun)) {
            return;
        }
        // 不允许趴下的武器
        if (!iGun.isCanCrawl(mainHandItem)) {
            return;
        }
        // 冷却时间没到，不执行
        if (crawCooldownTicks > 0) {
            return;
        }
        if (player.isSpectator() || player.isPassenger() || !player.onGround()) {
            return;
        }
        Identifier gunId = iGun.getGunId(mainHandItem);
        TimelessAPI.getClientGunIndex(gunId).ifPresent(gunIndex -> {
            this.isCrawling = true;
            this.crawCooldownTicks = COOLDOWN_TICKS;
            com.tacz.guns.util.TaczDebug.log("LocalPlayerCrawl: 发送 crawl=true");
            NetworkHandler.sendToServer(new ClientMessagePlayerCrawl(true));
        });
    }

    public void tickCrawl() {
        if (crawCooldownTicks > 0) {
            crawCooldownTicks--;
        }
        // 【按住趴下：松开即起身】
        // 不能靠输入事件的 action 判定松开。26.3 的常量是 PRESS=1 / RELEASE=0 / REPEAT=-1，
        // 但实测这条路（KeyboardHandlerMixin -> InputEvent.Key）只观测到 1 和 -1：
        // 12 次按下、55 次自动重复，**松开(0) 一次都没到过** —— 也就是说依赖
        // "action == RELEASE" 的分支在 26.3 上是死代码（原版这个 bug 就是这么来的）。
        // 所以以按键的「按住状态」为准：hold 模式下只要键不再按下，就立刻起身。
        // KeyMapping.isDown() 由原版自己维护，可靠且与事件管线无关。
        if (com.tacz.guns.config.client.KeyConfig.HOLD_TO_CRAWL.get()
                && !com.tacz.guns.client.input.CrawlKey.CRAWL_KEY.isDown()) {
            this.cancelCrawl();
            return;
        }
        // 持枪才能按键趴下
        ItemStack mainHandItem = player.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof IGun iGun)) {
            this.cancelCrawl();
            return;
        }
        // 不允许趴下的武器，则取消趴下状态
        if (!iGun.isCanCrawl(mainHandItem)) {
            this.cancelCrawl();
            return;
        }
        // 如果获取不到 gunIndex，则取消趴下状态
        Identifier gunId = iGun.getGunId(mainHandItem);
        if (TimelessAPI.getCommonGunIndex(gunId).isEmpty()) {
            this.cancelCrawl();
            return;
        }
        // 如果玩家是观察者模型、骑乘、跳跃、在游泳、不在地上，取消
        if (player.isSpectator() || player.isPassenger() || ((LivingEntityAccessor) player).tacz$isJumping() || player.isSwimming() || !player.onGround()) {
            this.cancelCrawl();
            return;
        }
        this.setCrawlPose();
    }

    /**
     * 取消趴下：清本地状态与强制姿态，并在「确实从趴着变成不趴」的那一次同步给服务端。
     *
     * <p>服务端 {@code LivingEntityCrawl#crawl(false)} 只是赋值，重复发是幂等的；
     * 但 {@code tickCrawl} 每 tick 都会调用本方法，所以只在状态真的发生变化时发包，
     * 避免每 tick 白刷一次。</p>
     */
    private void cancelCrawl() {
        boolean wasCrawling = this.isCrawling;
        this.isCrawling = false;
        this.setCrawlPose();
        if (wasCrawling) {
            // 【日志治理】这里每 tick 都会被调用，原先无条件打日志 —— 实测一局刷了 5218 行，
            // 占整份日志的 59%，其中绝大多数是"本来就没趴下"的空调用（参数还误传了两遍）。
            // 只在真的从趴下变成不趴（也就是确实要发包）时记一笔。
            com.tacz.guns.util.TaczDebug.log("LocalPlayerCrawl.cancelCrawl: 从趴下起身，已发包同步服务端");
            NetworkHandler.sendToServer(new ClientMessagePlayerCrawl(false));
        }
    }

    public boolean isCrawling() {
        return isCrawling;
    }

    private void setCrawlPose() {
        if (isCrawling) {
            ((ForcePoseInjection) player).tacz$setForcedPose(Pose.SWIMMING);
        } else {
            ((ForcePoseInjection) player).tacz$setForcedPose(null);
        }
    }
}
