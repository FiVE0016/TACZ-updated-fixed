package com.tacz.guns.client.gui.compat;

import com.mojang.blaze3d.Blaze3D;
import com.tacz.guns.client.input.ConfigKey;
import com.tacz.guns.config.TaczConfigs;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.util.Util;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 没装 Cloth Config 时的配置界面。
 *
 * <p>原本这里只有一句「去装 Cloth Config」和一个下载按钮。问题是 Cloth Config
 * <b>没有 Forge 26.3 版本</b>，于是 Forge 上「配置」按钮点开就只有那句话 ——
 * 等于整个客户端配置在游戏内不可达，其中就包括瞄具画中画（PIP）那几个开关。
 *
 * <p>所以这里改成一个用原版控件搭的最小配置页：把最常需要在游戏内改的几个
 * 瞄具/PIP 开关直接放出来，任何加载器、装不装 Cloth Config 都能用。完整的
 * 上百项配置仍然归 Cloth Config（下载按钮保留），这里只做「够用」的那一层。
 *
 * <p>开关值即时写回配置文件（{@code TaczConfigs.saveClientAndCommon()}），
 * 与 Cloth 那边的 savingRunnable 一致，返回上级时不会丢。
 */
public class FallbackConfigScreen extends Screen {
    public static final String CLOTH_CONFIG_URL = "https://www.curseforge.com/minecraft/mc-mods/cloth-config";
    private static final int ROW_W = 260;
    private static final int ROW_H = 20;
    private static final int GAP = 4;

    private final Screen lastScreen;
    /** 非 null 时正在等玩家按下要绑定的键。 */
    @Nullable
    private Button bindingButton;

    public FallbackConfigScreen(Screen lastScreen) {
        super(Component.translatable("gui.tacz.scope_config.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        int x = (this.width - ROW_W) / 2;
        int y = this.height / 4;

        y = addToggle(x, y, "config.tacz.client.render.scope_pip_enable",
                () -> RenderConfig.SCOPE_PIP_ENABLE.get(), RenderConfig.SCOPE_PIP_ENABLE::set);
        y = addToggle(x, y, "config.tacz.client.render.scope_pip_rerender",
                () -> RenderConfig.SCOPE_PIP_RERENDER.get(), RenderConfig.SCOPE_PIP_RERENDER::set);
        y = addToggle(x, y, "config.tacz.client.render.scope_pip_allow_shader_packs",
                () -> RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS.get(), RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS::set);
        // PIP 拿不到目镜掩码就没有孔径可贴，所以掩码开关也放出来 —— 否则玩家
        // 关掉掩码之后只会看到「PIP 不生效」，却找不到原因。
        y = addToggle(x, y, "config.tacz.client.render.scope_mask_enable",
                () -> RenderConfig.SCOPE_MASK_ENABLE.get(), RenderConfig.SCOPE_MASK_ENABLE::set);

        // 打开本界面的快捷键，默认不绑定（旧默认 T 会顶掉原版聊天）。点一下进入
        // 「等待按键」状态，按任意键绑定，Esc 解除绑定。写回原版的 options.txt，
        // 与原版「按键设置」界面改的是同一份数据。
        this.bindingButton = Button.builder(bindLabel(), b -> {
            this.bindingButton = b;
            b.setMessage(Component.literal("> ")
                    .append(Component.translatable("gui.tacz.scope_config.press_a_key"))
                    .append(" <"));
        }).bounds(x, y + GAP * 2, ROW_W, ROW_H).build();
        this.addRenderableWidget(this.bindingButton);
        y += ROW_H + GAP * 2;

        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.tacz.cloth_config_warning.download"), b -> openUrl(CLOTH_CONFIG_URL))
                        .bounds(x, y + GAP * 2, ROW_W, ROW_H).build()
        );
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_BACK, b -> this.minecraft.setScreenAndShow(this.lastScreen))
                        .bounds(x, y + GAP * 2 + ROW_H + GAP, ROW_W, ROW_H).build()
        );
    }


    private Component bindLabel() {
        Component bound = ConfigKey.OPEN_CONFIG_KEY.isUnbound()
                ? Component.translatable("gui.tacz.scope_config.unbound")
                : ConfigKey.OPEN_CONFIG_KEY.getTranslatedKeyMessage();
        return Component.translatable("gui.tacz.scope_config.open_key", bound);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.bindingButton != null) {
            // Esc 清空绑定，其余任意键直接绑上。
            InputConstants.Key key = event.key() == InputConstants.KEY_ESCAPE
                    ? InputConstants.UNKNOWN
                    : InputConstants.Type.KEYBOARD.getOrCreate(event.key());
            ConfigKey.OPEN_CONFIG_KEY.setKey(key);
            KeyMapping.resetMapping();
            if (this.minecraft != null) {
                this.minecraft.options.save();
            }
            this.bindingButton.setMessage(bindLabel());
            this.bindingButton = null;
            return true;
        }
        return super.keyPressed(event);
    }

    /** @return 下一行的 y */
    private int addToggle(int x, int y, String langKey, BooleanSupplier getter, Consumer<Boolean> setter) {
        this.addRenderableWidget(CycleButton.onOffBuilder(getter.getAsBoolean())
                .withTooltip(value -> Tooltip.create(Component.translatable(langKey + ".desc")))
                .create(x, y, ROW_W, ROW_H, Component.translatable(langKey), (button, value) -> {
                    setter.accept(value);
                    TaczConfigs.saveClientAndCommon();
                }));
        return y + ROW_H + GAP;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int pMouseX, int pMouseY, float pPartialTick) {
        // super draws the background, which in 26.3 includes the blur — calling extractBackground
        // as well makes it blur twice and vanilla throws "Can only blur once per frame".
        // (The old version of this class did exactly that, but was never instantiated, so the
        // bug stayed dormant until the screen was actually wired up.) Draw the labels after
        // super so they land on top, matching GunSmithTableScreen and GunRefitScreen.
        super.extractRenderState(gui, pMouseX, pMouseY, pPartialTick);
        gui.centeredText(this.font, this.title, this.width / 2, this.height / 4 - 28, 0xFFFFFFFF);
        gui.centeredText(this.font, Component.translatable("gui.tacz.scope_config.more"),
                this.width / 2, this.height / 4 - 16, 0xFFA0A0A0);
    }

    private void openUrl(String url) {
        if (StringUtils.isNotBlank(url) && minecraft != null) {
            // 26.3: ConfirmLinkScreen and Blaze3D.openUri both take a URI. Parse through vanilla
            // so a malformed or disallowed scheme is rejected before it can reach the browser.
            final URI uri;
            try {
                uri = Util.parseAndValidateUntrustedUri(url);
            } catch (URISyntaxException e) {
                return;
            }
            minecraft.setScreenAndShow(new ConfirmLinkScreen(yes -> {
                if (yes) {
                    Blaze3D.openUri(uri);
                }
                minecraft.setScreenAndShow(this);
            }, uri, true));
        }
    }
}
