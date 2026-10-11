package com.tacz.guns.client.input;

import cn.sh1rocu.tacz.api.event.InputEvent;
import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.client.KeyConfig;
import com.tacz.guns.config.sync.SyncConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

public class CrawlKey {
    public static final KeyMapping CRAWL_KEY = new KeyMapping("key.tacz.crawl.desc",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_C,
            TaCZKeyCategory.TACZ);

    public static void onCrawlPress(InputEvent.Key event) {
        boolean isCrawlKey = isInGame() && CRAWL_KEY.matches(InputConstants.Type.KEYBOARD.getOrCreate(event.getKey()));
        if (isCrawlKey) {
            com.tacz.guns.util.TaczDebug.log("CrawlKey: action={} (1=PRESS 0=RELEASE 2=REPEAT) holdToCrawl={} enableCrawl={}",
                    event.getAction(), KeyConfig.HOLD_TO_CRAWL.get(), SyncConfig.ENABLE_CRAWL.get());
        }
        if (isCrawlKey) {
            if (!SyncConfig.ENABLE_CRAWL.get()) {
                return;
            }
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.isSpectator() || player.isPassenger()) {
                return;
            }
            if (!(player instanceof IClientPlayerGunOperator operator)) {
                return;
            }
            // 「按住趴下」的松开事件必须先于所有持枪判断处理。
            // ⚠️ 26.3 上这个分支实际上是死代码：常量 PRESS=1/RELEASE=0/REPEAT=-1，
            // 但实测该事件管线只送得到 1（按下）和 -1（自动重复），松开(0) 从未出现。
            // 真正的松开判定放在 LocalPlayerCrawl#tickCrawl，用 KeyMapping 的按住状态做。
            // 这里保留，仅为兼容仍然按 GLFW 语义派发 action 的环境。
            if (KeyConfig.HOLD_TO_CRAWL.get() && event.getAction() == InputConstants.RELEASE) {
                com.tacz.guns.util.TaczDebug.log("CrawlKey: RELEASE -> crawl(false)");
                IClientPlayerGunOperator.fromLocalPlayer(player).crawl(false);
                return;
            }
            if (player.getMainHandItem().getItem() instanceof IGun iGun) {
                // 如果不允许下蹲，则禁止进行下蹲
                if (!iGun.isCanCrawl(player.getMainHandItem())) {
                    IClientPlayerGunOperator.fromLocalPlayer(player).crawl(false);
                    return;
                }
                boolean action = true;
                if (!KeyConfig.HOLD_TO_CRAWL.get()) {
                    action = !operator.isCrawl();
                }
                if (event.getAction() == InputConstants.PRESS) {
                    IClientPlayerGunOperator.fromLocalPlayer(player).crawl(action);
                }
            }
        }
    }

    public static boolean onCrawlControllerPress(boolean isPress) {
        if (!isInGame()) {
            return false;
        }
        if (!SyncConfig.ENABLE_CRAWL.get()) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator() || player.isPassenger()) {
            return false;
        }
        if (!(player instanceof IClientPlayerGunOperator operator)) {
            return false;
        }
        // 与键盘一致：按住模式下松开必须先处理，不受「手里是不是枪」影响。
        if (!isPress && KeyConfig.HOLD_TO_CRAWL.get()) {
            IClientPlayerGunOperator.fromLocalPlayer(player).crawl(false);
            return true;
        }
        if (!IGun.mainHandHoldGun(player)) {
            return false;
        }
        boolean action = true;
        if (!KeyConfig.HOLD_TO_CRAWL.get()) {
            action = !operator.isCrawl();
        }
        if (isPress) {
            IClientPlayerGunOperator.fromLocalPlayer(player).crawl(action);
            return true;
        }
        return false;
    }
}
