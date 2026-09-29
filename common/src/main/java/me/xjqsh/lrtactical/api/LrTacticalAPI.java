package me.xjqsh.lrtactical.api;

import me.xjqsh.lrtactical.api.item.IThrowable;
import me.xjqsh.lrtactical.item.index.ThrowableIndex;
import me.xjqsh.lrtactical.resource.CommonAssetsManager;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Optional;

/**
 * LRTactical 的对外查询入口。
 *
 * <p>当前公开投掷物、近战、消耗品三类 index 查询。历史：旧注释曾称“只暴露投掷物”，2026-08-12 复核更正。
 *
 * <p>The matching client-side display lookups are in {@link me.xjqsh.lrtactical.api.client.LrTacticalClientAPI}.
 * They moved out of this class because dedicated servers load it but have none of the display classes.
 */
public final class LrTacticalAPI {
    private LrTacticalAPI() {
    }

    /**
     * 取某个物品堆对应的投掷物定义。
     *
     * @return 该物品不是投掷物、或其 id 在数据包中没有对应定义时返回 empty
     */
    public static Optional<ThrowableIndex<?, ?>> getThrowableIndex(ItemStack stack) {
        if (!(stack.getItem() instanceof IThrowable item)) {
            return Optional.empty();
        }
        return Optional.ofNullable(CommonAssetsManager.get().getThrowableIndex(item.getId(stack)));
    }

    /** 所有已加载的投掷物定义（供创造标签页等遍历）。 */
    public static Collection<ThrowableIndex<?, ?>> getThrowableIndexes() {
        return CommonAssetsManager.get().getThrowableIndexes();
    }

    /**
     * 取某个物品堆对应的近战武器定义。
     *
     * @return 该物品不是近战武器、或其 id 在数据包中没有对应定义时返回 empty
     */
    public static Optional<me.xjqsh.lrtactical.item.index.MeleeWeaponIndex<?>> getMeleeIndex(ItemStack stack) {
        if (!(stack.getItem() instanceof me.xjqsh.lrtactical.api.item.IMeleeWeapon item)) {
            return Optional.empty();
        }
        return Optional.ofNullable(CommonAssetsManager.get().getMeleeIndex(item.getId(stack)));
    }

    /** 所有已加载的近战武器定义。 */
    public static Collection<me.xjqsh.lrtactical.item.index.MeleeWeaponIndex<?>> getMeleeIndexes() {
        return CommonAssetsManager.get().getMeleeIndexes();
    }

    public static Optional<me.xjqsh.lrtactical.item.index.ConsumableIndex> getConsumableIndex(ItemStack stack) {
        if (!(stack.getItem() instanceof me.xjqsh.lrtactical.api.item.IConsumable item)) {
            return Optional.empty();
        }
        return Optional.ofNullable(CommonAssetsManager.get().getConsumableIndex(item.getId(stack)));
    }

    public static Collection<me.xjqsh.lrtactical.item.index.ConsumableIndex> getConsumableIndexes() {
        return CommonAssetsManager.get().getConsumableIndexes();
    }
}
