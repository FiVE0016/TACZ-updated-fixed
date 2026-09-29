package me.xjqsh.lrtactical;

import me.xjqsh.lrtactical.init.ModCapabilities;
import me.xjqsh.lrtactical.init.ModCustomTypes;
import me.xjqsh.lrtactical.resource.CommonAssetsManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * LRTactical（LesRaisins Tactical Equipements）的 26.2 移植。
 *
 * <h2>移植来源与授权</h2>
 * <ul>
 *   <li>原作：{@code LesRaisins-Studios/LesRaisins-Tactical-Equipements}
 *       —— Programmer {@code xjqsh}，Artist {@code LeComte}，代码 GPL-3.0；</li>
 *   <li>参照的 1.21.1 NeoForge 移植：{@code Nahiyus512/...}（分支 {@code neoforge1.21.1}）
 *       —— 用作「1.20.1 → 1.21.1 有哪些改动」的地图。</li>
 * </ul>
 *
 * <h2>【重要】本移植<b>不包含</b>原作的美术资源</h2>
 * 原作 readme 明确声明 {@code Art Assets: All Rights Reserved}，
 * 因此本移植<b>只移植代码（GPL-3.0 允许）</b>，
 * 不打包、不分发原作的贴图 / 模型 / 音效。
 *
 * <p>这在架构上是可行的：LRTactical 的内容<b>主要由数据驱动</b> ——
 * 当前注册 throwable / melee / consumable / detonator 四个承载物品，并已接入各自逻辑；
 * 具体有哪些手雷、刀与消耗品来自 {@code data/<ns>/index/*}。flash_shield 尚未移植。
 * 因此本移植的定位是<b>纯前置框架</b>，由第三方内容包提供实际模型与内容。
 *
 * <h2>与本仓库主体（TACZ）的关系</h2>
 * 本包是<b>独立的附属模组代码</b>，与 {@code com.tacz.guns} 并列。
 * 它依赖 TACZ 的公开 API，但不修改 TACZ 自身。
 *
 * <h2>Entry points</h2>
 * The add-on has no loader entrypoint of its own; TACZ's loader-neutral hooks call into it on every loader:
 * <ul>
 *   <li>items, entity types, particle types, mob effects and the creative tab are registered from
 *       {@code com.tacz.guns.init.TaczRegistration}, each inside its own registry's window;</li>
 *   <li>{@link #init()} runs from {@code TaczCommonEvents#registerListeners};</li>
 *   <li>payloads are declared in {@code com.tacz.guns.network.NetworkHandler}; index sync, respawn cleanup,
 *       cancelling vanilla melee attacks and the index reload listeners go through {@code TaczCommonEvents};</li>
 *   <li>client registrations and ticks go through {@code ClientSetupEvent}, {@code TaczClientEvents} and
 *       {@code com.tacz.guns.client.init.ModEntitiesRender}.</li>
 * </ul>
 */
public final class EquipmentMod {
    public static final String MOD_ID = "lrtactical";
    /**
     * The upstream LRTactical version this port is based on. The add-on is bundled into TACZ instead of being a mod
     * of its own, so gun packs that declare a dependency on {@link #MOD_ID} are checked against this version.
     */
    public static final String UPSTREAM_VERSION = "0.3.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private EquipmentMod() {
    }

    /**
     * Sets up everything that isn't a registry entry. Runs once on both sides.
     */
    public static void init() {
        // Throwable and melee type tables (plain maps, not vanilla registries); the index loaders look types up here.
        ModCustomTypes.init();

        // 冷却计时器的 tick 驱动。
        //
        // 【易漏】这不是「注册某个内容」，而是注册一个每 tick 回调 ——
        // 上游对应 capability/TickHandler（@EventBusSubscriber 自动订阅），
        // 移植时整类漏掉，导致冷却永不结束、手雷一局只能用一次。
        // 详见 ModCapabilities#init 的完整根因分析。
        ModCapabilities.init();

        LOGGER.info("LRTactical (unofficial 26.3 port) initialized");
    }

    /**
     * Adds the loaders for {@code data/<ns>/index/{throwable,melee,consumable}/*.json}. These are server data;
     * remote clients receive the loaded files through {@code ServerMessageSyncLrPack}.
     */
    public static void registerServerReloadListeners(Consumer<PreparableReloadListener> register) {
        register.accept(CommonAssetsManager.get().getThrowableIndexManager());
        register.accept(CommonAssetsManager.get().getMeleeIndexManager());
        register.accept(CommonAssetsManager.get().getConsumableIndexManager());
    }
}
