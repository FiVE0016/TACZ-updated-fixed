package me.xjqsh.lrtactical.client.resource.display;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateContext;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.client.animation.statemachine.LuaStateMachineFactory;
import com.tacz.guns.client.resource.ClientAssetsManager;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 「延迟解析」的 Lua 状态机持有器。
 *
 * <h2>为什么不能像原来那样在 {@code create()} 里当场解析</h2>
 * LRTactical 的 display 是在<b>资源重载的 apply 阶段</b>同步建出来的
 * （{@code *DisplayManager#apply -> *DisplayInstance#create}），而 Lua 脚本表
 * （{@code ScriptManager#scriptMap}）是在同一轮重载的另一个 listener 里填的。
 * 只要两者的先后顺序不如预期，或者内容包引用的状态机位于重载期没被扫到的命名空间
 * （典型例子：一个枪包里除主命名空间外的第二个命名空间），原来的实现就会抛
 * {@code no corresponding state machine found for <id>}，
 * 于是<b>整件物品的 display 被判失败</b> —— 物品没有模型、没有动画、没有音效。
 *
 * <p>现在改成：{@code create()} 只记下「要哪个脚本」，等第一次真正要用（首帧渲染）时
 * 才去解析；那时重载早已结束，脚本表一定是最终状态。</p>
 *
 * <h2>两级降级</h2>
 * <ol>
 *   <li>内容包请求的脚本拿不到 → 退到同族的<b>内置状态机</b>（{@code fallback}），
 *       物品至少能动（拔枪/闲置/使用这些通用姿态）；</li>
 *   <li>连内置的也拿不到 → 返回 {@code null}。调用方（{@code AnimateGeoItemRenderer}
 *       及其子类）本就判空，物品退化成静态模型，而不是整件消失。</li>
 * </ol>
 *
 * <p>第 2 种情况会打一条 ERROR，并把「请求的模块名 + 当前已加载脚本数 + 相近的模块名」
 * 一起打出来 —— 这类问题以前只表现为一句没有上下文的
 * {@code no corresponding state machine found}，很难定位。</p>
 *
 * @param <T> 状态机上下文类型
 */
public final class LazyStateMachine<T extends AnimationStateContext> {
    private final Identifier requested;
    @Nullable
    private final Identifier fallback;
    private final AnimationController controller;
    /** 日志里用来指认是哪个 display / 物品。 */
    private final String owner;

    private boolean resolved;
    @Nullable
    private LuaAnimationStateMachine<T> machine;

    public LazyStateMachine(Identifier requested, @Nullable Identifier fallback,
                           AnimationController controller, String owner) {
        this.requested = requested;
        this.fallback = fallback;
        this.controller = controller;
        this.owner = owner;
    }

    /**
     * 解析顺序：请求的脚本 → 内置兜底脚本 → {@code null}。只解析一次，结果被缓存
     * （display 每次资源重载都会重建，所以不需要额外的失效逻辑）。
     */
    @Nullable
    public LuaAnimationStateMachine<T> get() {
        if (!resolved) {
            resolved = true;
            machine = resolve();
        }
        return machine;
    }

    @Nullable
    private LuaAnimationStateMachine<T> resolve() {
        var script = ClientAssetsManager.INSTANCE.getScript(requested);
        if (script == null && fallback != null && !fallback.equals(requested)) {
            script = ClientAssetsManager.INSTANCE.getScript(fallback);
            if (script != null) {
                GunMod.LOGGER.warn("[LRTactical] {} 请求的状态机 {} 不可用，回退到内置状态机 {}",
                        owner, requested, fallback);
            }
        }
        if (script == null) {
            logMissing();
            return null;
        }
        return new LuaStateMachineFactory<T>()
                .setController(controller)
                .setLuaScripts(script)
                .build();
    }

    private void logMissing() {
        var loaded = ClientAssetsManager.INSTANCE.getLoadedScriptModuleNames();
        List<String> similar = loaded.stream()
                .filter(name -> name.endsWith(requested.getPath()))
                .limit(4)
                .toList();
        GunMod.LOGGER.error("[LRTactical] {} 请求的状态机 {} 不存在（已加载 {} 个脚本{}）；该物品将退化为静态模型",
                owner, requested, loaded.size(),
                similar.isEmpty() ? "" : "，同路径但不同命名空间的脚本: " + similar);
    }
}
