package com.tacz.guns.client.input;
import com.tacz.guns.platform.Platform;

import cn.sh1rocu.tacz.api.event.InputEvent;
import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.client.gui.compat.FallbackConfigScreen;
import com.tacz.guns.compat.cloth.MenuIntegration;
import com.tacz.guns.init.CompatRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;


import static com.tacz.guns.util.InputExtraCheck.isInGame;

public class ConfigKey {
    /**
     * 默认<b>不绑定</b>。原先默认是 T，而 T 是原版的聊天键 —— 装上就把聊天顶掉了。
     * 配置界面本来就能从模组列表的 Config 按钮（Forge/NeoForge）或 Mod Menu
     * （Fabric/Quilt）打开，这个键只是快捷方式，所以让玩家自己决定绑哪个：
     * 配置界面里有一个「配置界面快捷键」的设置项，原版的按键设置界面里也能改。
     */
    public static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping("key.tacz.open_config.desc",
            InputConstants.Type.KEYBOARD,
            InputConstants.UNKNOWN.getValue(),
            TaCZKeyCategory.TACZ);

    public static void onOpenConfig(InputEvent.Key event) {
        // 未绑定时绝不响应：UNKNOWN 的键值是 0，真去 matches 有可能被值为 0 的事件误中。
        if (OPEN_CONFIG_KEY.isUnbound()) {
            return;
        }
        if (isInGame() && event.getAction() == InputConstants.PRESS
                && OPEN_CONFIG_KEY.matches(InputConstants.Type.KEYBOARD.getOrCreate(event.getKey()))) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.isSpectator()) {
                return;
            }
            if (!Platform.INSTANCE.isModLoaded(CompatRegistry.CLOTH_CONFIG)) {
                // Cloth Config has no Forge 26.3 build, and this key used to do nothing but post a
                // "go install it" chat line — which left the scope/PIP toggles unreachable in game.
                // Open TACZ's own small screen instead; it still links to Cloth Config for the rest.
                Minecraft.getInstance().gui.setScreen(new FallbackConfigScreen(null));
            } else {
                CompatRegistry.checkModLoad(CompatRegistry.CLOTH_CONFIG, () -> Minecraft.getInstance().gui.setScreen(MenuIntegration.getConfigScreen(null)));
            }
        }
    }
}
