package com.tacz.guns.client.gameplay;

import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * 采样「第一人称枪口相对眼睛的世界轴偏移」，交给开火包带给服务端。
 *
 * <h2>为什么需要它</h2>
 * 弹丸实体是在<b>服务端</b>创建的（{@code ModernKineticGunScriptAPI#spawnProjectiles}），
 * 而枪口是<b>客户端模型</b>里的一个节点 —— 服务端没有模型数据，所以只能由客户端算好偏移发过去。
 *
 * <h2>生命周期</h2>
 * <ul>
 *   <li>{@link #capture()} 只在客户端<b>主线程</b>调用（{@code LocalPlayerShoot#shoot} 入口）。
 *       真正发包在 scheduler 线程上，那时再去读相机/渲染状态是不安全的（见
 *       {@code LocalPlayerShoot} 里那段「必须经 Minecraft 事件循环提交」的注释），
 *       所以在主线程先取好快照。</li>
 *   <li>{@link #get()} 在发包时读取，<b>故意不清空</b>：连发时后续几发复用同一份快照
 *       （枪口相对眼睛的位置在这段时间里基本不变），直到下一次 {@code shoot()} 重新采样。</li>
 * </ul>
 *
 * <h2>何时返回 null</h2>
 * 第三人称、模型里没有 {@code muzzle_flash} 节点、或偏移过小时都返回 null ——
 * 服务端拿到 null 就按改动前的行为在眼睛处生成（{@code resolveMuzzleOrigin} 会退回）。
 * 视图空间 → 世界轴的换算与 {@code EntityBulletRenderer#renderTracerAmmo} 保持同一套
 * （{@code camera.rotation()} 即视图→世界，直接 rotate）。
 */
public final class ClientMuzzleOffset {
    @Nullable
    private static volatile Vector3f worldOffset;

    private ClientMuzzleOffset() {
    }

    /** 采样当前枪口偏移；非第一人称或没有枪口节点时置为 null。只能在主线程调用。 */
    public static void capture() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.options.getCameraType().isFirstPerson()) {
            com.tacz.guns.util.TaczDebug.log("ClientMuzzleOffset.capture: 无玩家或非第一人称 -> null (cameraType={})",
                    minecraft.options.getCameraType());
            worldOffset = null;
            return;
        }
        // 必须按「当前手持的这把枪」取偏移：muzzleRenderOffset 是全局静态量，
        // 换枪后它还是上一把枪的值（默认包的 m320/rpg7 模型没有 muzzle_flash 节点，
        // 更会一直沿用上一把枪的偏移 —— 表现为榴弹从枪口上方飞出去）。
        net.minecraft.world.item.ItemStack held = minecraft.player.getMainHandItem();
        net.minecraft.resources.Identifier heldGunId = held.getItem() instanceof com.tacz.guns.api.item.IGun gun
                ? gun.getGunId(held) : null;
        Vector3f viewSpace = GunItemRendererWrapper.getViewMuzzleOffsetFor(heldGunId);
        if (viewSpace == null) {
            com.tacz.guns.util.TaczDebug.log("ClientMuzzleOffset.capture: 枪={} 没有可用的枪口偏移（未渲染过/换枪后陈旧）-> null", heldGunId);
            worldOffset = null;
            return;
        }
        // 注意：JOML 的 rotate() 是原地修改 —— 先留一份副本，否则日志里 view 与 world 会打印成同一个值
        Vector3f viewCopy = new Vector3f(viewSpace);
        worldOffset = viewSpace.rotate(minecraft.gameRenderer.mainCamera().rotation());
        com.tacz.guns.util.TaczDebug.log("ClientMuzzleOffset.capture: 枪={} view=({}, {}, {}) -> world=({}, {}, {}) len={}",
                heldGunId,
                String.format("%.3f", viewCopy.x), String.format("%.3f", viewCopy.y), String.format("%.3f", viewCopy.z),
                String.format("%.3f", worldOffset.x), String.format("%.3f", worldOffset.y), String.format("%.3f", worldOffset.z),
                String.format("%.3f", worldOffset.length()));
    }

    /** @return 最近一次采样到的世界轴枪口偏移；无有效值时返回 null。 */
    @Nullable
    public static Vector3f get() {
        return worldOffset;
    }
}
