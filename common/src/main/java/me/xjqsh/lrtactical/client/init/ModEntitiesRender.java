package me.xjqsh.lrtactical.client.init;

import cn.sh1rocu.tacz.api.extension.IItem;
import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import com.tacz.guns.client.init.ClientSetupEvent;
import com.tacz.guns.platform.IdentifiableReloadListener;
import me.xjqsh.lrtactical.client.particle.SmokeCloudParticle;
import me.xjqsh.lrtactical.client.renderer.entity.ThrowableEntityRenderer;
import me.xjqsh.lrtactical.client.renderer.item.HasCustomDisplayProperty;
import me.xjqsh.lrtactical.client.renderer.item.LrDynamicItemModel;
import me.xjqsh.lrtactical.client.resource.LrClientAssetsManager;
import me.xjqsh.lrtactical.entity.EffectCloudGrenadeEntity;
import me.xjqsh.lrtactical.entity.GrenadeEntity;
import me.xjqsh.lrtactical.entity.SmokeGrenadeEntity;
import me.xjqsh.lrtactical.entity.StickyGrenadeEntity;
import me.xjqsh.lrtactical.entity.StunGrenadeEntity;
import me.xjqsh.lrtactical.entity.sp.SpEffectCloudEntity;
import me.xjqsh.lrtactical.init.ModItems;
import me.xjqsh.lrtactical.init.ModParticleTypes;
import me.xjqsh.lrtactical.mixin.accessor.ConditionalItemModelPropertiesAccessor;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.world.item.Item;

import java.util.function.Consumer;

/**
 * LRTactical's client-side registrations. Each method is called from the TACZ hook of the same kind, which every
 * loader drives; the HUD layers and tooltip components are listed directly in {@code ClientSetupEvent}.
 */
public final class ModEntitiesRender {
    private ModEntitiesRender() {
    }

    /**
     * 实体渲染器注册。Called from {@code com.tacz.guns.client.init.ModEntitiesRender#registerEntityRenderers}.
     *
     * <h2>为什么必须有</h2>
     * 实体类型注册了但<b>没有对应渲染器</b>时，客户端不会「不画它」，
     * 而是在 {@code EntityRenderDispatcher#shouldRender} 处直接
     * <b>抛 NullPointerException 导致游戏崩溃</b>
     * （{@code Cannot invoke "EntityRenderer.shouldRender(...)" because "renderer" is null}）。
     *
     * <p>第 5 步遗漏了这一步，表现为「手雷一扔出去就崩」——
     * 实体本身已成功生成，崩在客户端渲染阶段。
     *
     * <h2>【动画层补完后的更新】改用自建的 {@code ThrowableEntityRenderer}</h2>
     * 原先这里用原版 {@code ThrownItemRenderer}，理由是「本移植不打包美术资源，
     * 用原版渲染器即可」。补完动画层后这个理由不再成立 —— 装了内容包时，
     * 原版渲染器会把手雷画成<b>永远正对镜头的平面贴片</b>，
     * 既丢失飞行姿态，也无法隐藏 {@code entity_hide}（拉环/保险销）组。
     *
     * <p>{@code ThrowableEntityRenderer} 内部仍走
     * {@code ItemModelResolver#updateForTopItem}，因此<b>没装内容包时行为不变</b>
     * （照样是原版物品模型），只是多了朝向旋转。
     */
    public static void registerEntityRenderers(com.tacz.guns.client.init.ModEntitiesRender.Registrar registrar) {
        // 【必须】每新增一种投掷物实体都要在这里加一行，否则一进视野就 NPE 崩溃。
        // 五种投掷物实体都继承 ThrowableItemEntity，故共用同一个渲染器。
        registrar.registerEntityRenderer(GrenadeEntity.TYPE, ThrowableEntityRenderer::new);
        registrar.registerEntityRenderer(StickyGrenadeEntity.TYPE, ThrowableEntityRenderer::new);
        registrar.registerEntityRenderer(SmokeGrenadeEntity.TYPE, ThrowableEntityRenderer::new);
        registrar.registerEntityRenderer(EffectCloudGrenadeEntity.TYPE, ThrowableEntityRenderer::new);
        registrar.registerEntityRenderer(StunGrenadeEntity.TYPE, ThrowableEntityRenderer::new);
        // 效果云本体用 NoopRenderer：云自身【不绘制任何模型】，视觉完全由粒子构成。
        // 原版 AreaEffectCloud 用的正是它（EntityRenderers 常量池确认引用了
        // NoopRenderer.<init>(EntityRendererProvider$Context)）。
        // 注意它同样【必须注册】—— 没有渲染器一进视野就 NPE 崩溃，
        // 「不需要画」和「不注册」是两回事。
        registrar.registerEntityRenderer(SpEffectCloudEntity.TYPE, NoopRenderer::new);
    }

    /**
     * 粒子工厂注册。Called from {@code ClientSetupEvent#registerParticleProviders}.
     *
     * <p>用带 {@code SpriteSet} 的注册方式 —— 贴图图集就绪后回调并给出 {@code SpriteSet}（sprite 来自
     * {@code assets/<ns>/particles/<name>.json}，与原版规则一致）。
     * 不带 SpriteSet 的方式适用于自绘贴图的粒子（本仓库 {@code BulletHoleParticle} 走的是那条）。
     */
    public static void registerParticles(ClientSetupEvent.ParticleRegistrar registrar) {
        registrar.registerSpriteSet(ModParticleTypes.SMOKE_CLOUD, SmokeCloudParticle.Provider::new);
    }

    /**
     * 动画/渲染层的客户端注册。Called from {@code ClientSetupEvent#registerItemModelTypes}.
     *
     * <p><b>必须在客户端物品 JSON 解码之前调用</b> —— 两个注册项都是「JSON 里会引用的类型」，
     * 注册晚了会在解码 {@code items/*.json} 时报「未知类型」，
     * 表现为物品完全没有模型（而不是回退到默认模型）。
     * 调用点因此与 TACZ 的 {@code TaczDynamicItemModel.registerType()} 并排。
     */
    public static void registerItemModels() {
        // 客户端物品模型类型 lrtactical:dynamic_item
        LrDynamicItemModel.registerType();
        // 条件属性 lrtactical:has_custom_display（用于「有无内容包」的模型分流）
        ConditionalItemModelPropertiesAccessor.lrtactical$getIdMapper()
                .put(HasCustomDisplayProperty.ID, HasCustomDisplayProperty.MAP_CODEC);
    }

    /**
     * 注册 display 资源加载器（{@code assets/<ns>/display/**}）。
     *
     * <p>Called from {@code ClientSetupEvent#registerClientReloadListeners} after TACZ's own listeners, which these
     * depend on; see {@link LrClientAssetsManager}.
     */
    public static void registerReloadListeners(Consumer<IdentifiableReloadListener> register) {
        LrClientAssetsManager.INSTANCE.reloadAndRegister(register);
    }

    /**
     * 把本模块的物品与其自定义渲染器登记进 {@code BuiltinItemRendererRegistry}。
     *
     * <p>TACZ 侧的 {@code ClientSetupEvent#registerBuiltinItemRenderers} 是遍历整个物品注册表、挑出
     * {@code instanceof IItem} 的来注册。LRTactical 的物品同样实现了
     * {@code IItem}，因此<b>会被那段遍历一并覆盖</b> —— 本方法只是把这层依赖
     * 显式化，便于日后 TACZ 侧改写遍历逻辑时不至于静默失效。
     * 重复注册是幂等的（底层是 {@code IdentityHashMap#put}）。
     */
    public static void registerItemRenderers() {
        register(ModItems.MELEE);
        register(ModItems.THROWABLE);
        // 官方 0.4.3：消耗品也有自己的 Bedrock/Lua 渲染通道
        register(ModItems.CONSUMABLE);
    }

    private static void register(Item item) {
        if (item instanceof IItem ext) {
            var renderer = ext.getCustomRenderer();
            if (renderer != null) {
                BuiltinItemRendererRegistry.INSTANCE.register(item, renderer);
            }
        }
    }
}
