package me.xjqsh.lrtactical.api.client;

import me.xjqsh.lrtactical.api.item.IConsumable;
import me.xjqsh.lrtactical.api.item.IMeleeWeapon;
import me.xjqsh.lrtactical.api.item.IThrowable;
import me.xjqsh.lrtactical.client.resource.LrClientAssetsManager;
import me.xjqsh.lrtactical.client.resource.display.ConsumableDisplayInstance;
import me.xjqsh.lrtactical.client.resource.display.MeleeDisplayInstance;
import me.xjqsh.lrtactical.client.resource.display.ThrowableDisplayInstance;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Client-only lookups of LRTactical's display data. These used to be part of {@code LrTacticalAPI}; dedicated servers
 * load that class but have no display classes, so they live here and must only be called on the client.
 *
 * <p>与 {@code LrTacticalAPI} 里的 index 是【两套独立通道】：
 * <pre>
 *   index   -> data/&lt;ns&gt;/index/**      数据包，服务端权威，需网络同步
 *   display -> assets/&lt;ns&gt;/display/**  资源包，纯客户端，可被材质包覆盖
 * </pre>
 * 因此查询也分开，不能互相回退。
 *
 * <p>【为什么按 getDisplayId 而不是 getId 查】
 * ICustomItem 允许「同一种手雷显示成不同外观」（OVERRIDE_DISPLAY_ID），
 * display 走的正是这个 id。上游此处也是 getDisplayId，行为保持一致。
 */
public final class LrTacticalClientAPI {
    private LrTacticalClientAPI() {
    }

    /**
     * 取某个物品堆对应的投掷物<b>客户端展示数据</b>。
     *
     * @return 该物品不是投掷物、或内容包没有为它提供 display 时返回 empty
     *         （此时渲染器应回退到原版物品模型，而不是不画）
     */
    public static Optional<ThrowableDisplayInstance> getThrowableDisplay(ItemStack stack) {
        if (!(stack.getItem() instanceof IThrowable item)) {
            return Optional.empty();
        }
        return Optional.ofNullable(LrClientAssetsManager.INSTANCE.getThrowableDisplay(item.getDisplayId(stack)));
    }

    /**
     * 取某个物品堆对应的近战武器<b>客户端展示数据</b>。
     *
     * @return 该物品不是近战武器、或内容包没有为它提供 display 时返回 empty
     */
    public static Optional<MeleeDisplayInstance> getMeleeDisplay(ItemStack stack) {
        if (!(stack.getItem() instanceof IMeleeWeapon item)) {
            return Optional.empty();
        }
        return Optional.ofNullable(LrClientAssetsManager.INSTANCE.getMeleeDisplay(item.getDisplayId(stack)));
    }

    /**
     * 取某个物品堆对应的消耗品<b>客户端展示数据</b>（官方 0.4.3 新增的通道）。
     *
     * @return 该物品不是消耗品、或内容包没有为它提供 display 时返回 empty
     *         （此时 {@code HasCustomDisplayProperty} 为 false，
     *         {@code items/consumable.json} 会回退到原版占位模型）
     */
    public static Optional<ConsumableDisplayInstance> getConsumableDisplay(ItemStack stack) {
        if (!(stack.getItem() instanceof IConsumable item)) {
            return Optional.empty();
        }
        return Optional.ofNullable(LrClientAssetsManager.INSTANCE.getConsumableDisplay(item.getDisplayId(stack)));
    }
}
