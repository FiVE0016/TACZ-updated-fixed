package com.tacz.guns.client.init;

import cn.sh1rocu.tacz.api.extension.IItem;
import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import cn.sh1rocu.tacz.compat.meshloader.TaczMeshyIntegration;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.other.ThirdPersonManager;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import com.tacz.guns.client.gui.overlay.HeatBarOverlay;
import com.tacz.guns.client.gui.overlay.InteractKeyTextOverlay;
import com.tacz.guns.client.gui.overlay.KillAmountOverlay;
import com.tacz.guns.client.gui.overlay.ScopeMaskDebugOverlay;
import com.tacz.guns.client.input.*;
import com.tacz.guns.client.particle.BulletHoleParticle;
import com.tacz.guns.client.renderer.item.AmmoBoxStatueProperty;
import com.tacz.guns.client.renderer.item.TaczDynamicItemModel;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.tooltip.ClientAmmoBoxTooltip;
import com.tacz.guns.client.tooltip.ClientAttachmentItemTooltip;
import com.tacz.guns.client.tooltip.ClientBlockItemTooltip;
import com.tacz.guns.client.tooltip.ClientGunTooltip;
import com.tacz.guns.compat.ar.ARCompat;
import com.tacz.guns.compat.controllable.ControllableCompat;
import com.tacz.guns.compat.firstperson.FirstPersonAnimationCompat;
import com.tacz.guns.compat.immediatelyfast.ImmediatelyFastCompat;
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import com.tacz.guns.compat.shouldersurfing.ShoulderSurfingCompat;
import com.tacz.guns.compat.zoomify.ZoomifyCompat;
import com.tacz.guns.init.ModParticles;
import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.inventory.tooltip.AmmoBoxTooltip;
import com.tacz.guns.inventory.tooltip.AttachmentItemTooltip;
import com.tacz.guns.inventory.tooltip.BlockItemTooltip;
import com.tacz.guns.inventory.tooltip.GunTooltip;
import com.tacz.guns.mixin.accessor.SelectItemModelPropertiesAccessor;
import com.tacz.guns.platform.IdentifiableReloadListener;
import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.client.overlay.BlindnessOverlay;
import me.xjqsh.lrtactical.client.overlay.UsingProgressOverlay;
import me.xjqsh.lrtactical.client.tooltip.ClientConsumableTooltip;
import me.xjqsh.lrtactical.client.tooltip.ClientMeleeTooltip;
import me.xjqsh.lrtactical.client.tooltip.ClientThrowableTooltip;
import me.xjqsh.lrtactical.inventory.tooltip.ConsumableTooltip;
import me.xjqsh.lrtactical.inventory.tooltip.MeleeTooltip;
import me.xjqsh.lrtactical.inventory.tooltip.ThrowableTooltip;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Client registrations that every loader performs through its own API: this class supplies the
 * content (key mappings, tooltip components, HUD layers, reload listeners), the loaders wire it in.
 * The bundled LRTactical add-on registers through the same methods.
 */
public class ClientSetupEvent {
    public static List<KeyMapping> keyMappings() {
        return List.of(
                InspectKey.INSPECT_KEY,
                ReloadKey.RELOAD_KEY,
                ShootKey.SHOOT_KEY,
                InteractKey.INTERACT_KEY,
                FireSelectKey.FIRE_SELECT_KEY,
                AimKey.AIM_KEY,
                CrawlKey.CRAWL_KEY,
                RefitKey.REFIT_KEY,
                ZoomKey.ZOOM_KEY,
                MeleeKey.MELEE_KEY,
                ConfigKey.OPEN_CONFIG_KEY
        );
    }

    /**
     * TACZ's and LRTactical's tooltip components and the client-side components that draw them.
     */
    public static List<TooltipFactory<?>> tooltipComponentFactories() {
        return List.of(
                new TooltipFactory<>(GunTooltip.class, ClientGunTooltip::new),
                new TooltipFactory<>(AmmoBoxTooltip.class, ClientAmmoBoxTooltip::new),
                new TooltipFactory<>(AttachmentItemTooltip.class, ClientAttachmentItemTooltip::new),
                new TooltipFactory<>(BlockItemTooltip.class, ClientBlockItemTooltip::new),
                new TooltipFactory<>(ThrowableTooltip.class, ClientThrowableTooltip::new),
                new TooltipFactory<>(MeleeTooltip.class, ClientMeleeTooltip::new),
                new TooltipFactory<>(ConsumableTooltip.class, ClientConsumableTooltip::new)
        );
    }

    public record TooltipFactory<T extends TooltipComponent>(Class<T> type, Function<? super T, ? extends ClientTooltipComponent> factory) {
        @Nullable
        public ClientTooltipComponent tryCreate(TooltipComponent tooltip) {
            return type.isInstance(tooltip) ? factory.apply(type.cast(tooltip)) : null;
        }
    }

    /**
     * @return the client-side component for one of TACZ's tooltip components, or {@code null} for anything else
     */
    @Nullable
    public static ClientTooltipComponent createTooltipComponent(TooltipComponent tooltip) {
        for (TooltipFactory<?> factory : tooltipComponentFactories()) {
            ClientTooltipComponent component = factory.tryCreate(tooltip);
            if (component != null) {
                return component;
            }
        }
        return null;
    }

    /**
     * Receives TACZ's particle providers; each loader forwards them to its own registration API.
     */
    public interface ParticleRegistrar {
        <T extends ParticleOptions> void registerSpecial(ParticleType<T> type, ParticleProvider<T> provider);

        <T extends ParticleOptions> void registerSpriteSet(ParticleType<T> type, ParticleResources.SpriteParticleRegistration<T> registration);
    }

    public static void registerParticleProviders(ParticleRegistrar registrar) {
        registrar.registerSpecial(ModParticles.BULLET_HOLE, new BulletHoleParticle.Provider());
        me.xjqsh.lrtactical.client.init.ModEntitiesRender.registerParticles(registrar);
    }

    /**
     * Receives TACZ's menu screens; each loader forwards them to its own registration API.
     */
    public interface MenuScreenRegistrar {
        <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(MenuType<? extends M> type, MenuScreens.ScreenConstructor<M, U> constructor);
    }

    public static void registerMenuScreens(MenuScreenRegistrar registrar) {
        registrar.register(GunSmithTableMenu.TYPE, GunSmithTableScreen::new);
    }

    /**
     * TACZ's and LRTactical's HUD layers, drawn above vanilla's HUD in this order.
     */
    public static List<HudLayer> hudLayers() {
        return List.of(
                new HudLayer(id("gun_hud"), (graphics, deltaTracker) -> GunHudOverlay.render(graphics, deltaTracker.getRealtimeDeltaTicks())),
                new HudLayer(id("heat_bar"), (graphics, deltaTracker) -> HeatBarOverlay.render(graphics, deltaTracker.getRealtimeDeltaTicks())),
                new HudLayer(id("interact_key_text"), (graphics, deltaTracker) -> InteractKeyTextOverlay.render(graphics, deltaTracker.getRealtimeDeltaTicks())),
                new HudLayer(id("kill_amount"), (graphics, deltaTracker) -> KillAmountOverlay.render(graphics, deltaTracker.getRealtimeDeltaTicks())),
                // 【Step 1 调试】瞄具掩码预览，由 RenderConfig.SCOPE_MASK_DEBUG 控制，默认关闭。
                new HudLayer(id("scope_mask_debug"), (graphics, deltaTracker) -> ScopeMaskDebugOverlay.render(graphics, deltaTracker.getRealtimeDeltaTicks())),
                // LRTactical: use, cook and melee cooldown progress bars.
                new HudLayer(lrId("using_progress"), (graphics, deltaTracker) -> UsingProgressOverlay.render(graphics, deltaTracker.getRealtimeDeltaTicks())),
                // LRTactical: the flashbang blindness mask goes last so that it covers every other HUD element.
                new HudLayer(lrId("blindness"), (graphics, deltaTracker) -> BlindnessOverlay.render(graphics, deltaTracker.getRealtimeDeltaTicks()))
        );
    }

    public record HudLayer(Identifier id, BiConsumer<GuiGraphicsExtractor, DeltaTracker> renderer) {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GunMod.MOD_ID, path);
    }

    private static Identifier lrId(String path) {
        return Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, path);
    }

    /**
     * Registers the custom item model types used by TACZ's and LRTactical's {@code items/*.json}. Must run
     * before the client first loads its resources, or those item definitions fail to decode.
     */
    public static void registerItemModelTypes() {
        // 26.2 client item JSONs use the custom tacz:dynamic_item ItemModel type.
        TaczDynamicItemModel.registerType();
        // 弹药盒外观变体属性（tacz:ammo_statue），供 items/ammo_box.json 的 select 使用。
        SelectItemModelPropertiesAccessor.tacz$getIdMapper().put(AmmoBoxStatueProperty.ID, AmmoBoxStatueProperty.TYPE);
        // Built-in TacZ Mesh Loader: gun model_type "mesh" must be registered before gun pack displays load.
        TaczMeshyIntegration.onClientSetup();
        // LRTactical: lrtactical:dynamic_item and the lrtactical:has_custom_display condition.
        me.xjqsh.lrtactical.client.init.ModEntitiesRender.registerItemModels();
    }

    /**
     * Binds TACZ's items to their dynamic renderers; call once items are registered.
     */
    public static void registerBuiltinItemRenderers() {
        // getCustomRenderer() 允许返回 null —— 表示「该物品走原版模型渲染」。
        // 弹药盒就是这种情况（改用 items/ammo_box.json 的 select + 9 个变体模型）。
        // LRTactical's items implement IItem as well and are bound here too.
        BuiltInRegistries.ITEM.stream().filter(item -> item instanceof IItem).forEach(item -> {
            BuiltinItemRendererRegistry.DynamicItemRenderer renderer = ((IItem) item).getCustomRenderer();
            if (renderer != null) {
                BuiltinItemRendererRegistry.INSTANCE.register(item, renderer);
            }
        });
    }

    public static void onClientSetup() {
        // 注册自己的的硬编码第三人称动画
        ThirdPersonManager.registerDefault();

        // 26.2 已解决: ColorProviderRegistry.ITEM 与 ItemProperties 均已移除。
        // 弹药箱染色改由 items/ammo_box.json 模型里的 minecraft:dye tint 完成；
        // 变体选择改由 minecraft:select + tacz:ammo_statue 属性完成
        // （属性实现见 AmmoBoxStatueProperty，注册点见 registerItemModelTypes）。

        // 第一人称身体/手部动画 Mod：持有 TACZ 动画物品时交还 viewmodel 渲染权。
        FirstPersonAnimationCompat.init();

        // 与 Shoulder Surfing Reloaded 的兼容
        ShoulderSurfingCompat.init();

        // Zoomify (Fabric/Quilt only on 26.2)
        ZoomifyCompat.init();

        // 与 Controllable 的兼容
        ControllableCompat.init();

        // 与 Accelerated Rendering 的兼容
        ARCompat.init();

        // ImmediatelyFast compatibility (a no-op facade on 26.2, see ImmediatelyFastCompat).
        ImmediatelyFastCompat.init();
    }

    /**
     * Hands the client-side reload listeners (gun pack displays, models, animations, scripts) to the loader.
     */
    public static void registerClientReloadListeners(Consumer<IdentifiableReloadListener> register) {
        PlayerAnimatorCompat.init();

        ClientAssetsManager.INSTANCE.reloadAndRegister(register);
        if (PlayerAnimatorCompat.isInstalled()) {
            PlayerAnimatorCompat.registerReloadListener(register);
        }
        // LRTactical's display listeners fetch models, animations and scripts from TACZ's managers while they
        // apply. Listeners apply in the order they are added, so these must be handed out after TACZ's.
        me.xjqsh.lrtactical.client.init.ModEntitiesRender.registerReloadListeners(register);
        // Built-in TacZ Mesh Loader: drops its geo parse cache at the end of each reload.
        // Registered after the asset listeners, the same order the fork used.
        register.accept(TaczMeshyIntegration.parseCacheReloadListener());
    }
}
