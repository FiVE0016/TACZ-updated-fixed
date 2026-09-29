package com.tacz.guns.api.client.other;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 用来在收物品时，让其保持一段时间渲染的接口
 */
public interface KeepingItemRenderer {
    /**
     * 物品保持渲染的时间
     *
     * @param itemStack 保持的物品
     * @param timeMs    时间，单位毫秒
     */
    void keep(ItemStack itemStack, long timeMs);

    /**
     * 获取当前主手正在渲染的物品
     */
    ItemStack getCurrentItem();

    /**
     * 26.3 起，主手物品与装备高度这些状态从 {@code ItemInHandRenderer} 搬到了
     * {@code LocalPlayer#firstPersonHandsAndItems()}（渲染器本身已无状态），
     * TACZ 的 Mixin 也跟着搬到那里实现本接口。
     *
     * @return 本地玩家的 {@code FirstPersonHandsAndItems} 实例；没有本地玩家时返回一个
     * 什么都不保持的空实现，让调用点不必各自判空 —— 26.2 的
     * {@code getCurrentItem} 在无玩家时同样只会给出一个空物品。
     */
    static KeepingItemRenderer getRenderer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return Empty.INSTANCE;
        }
        return (KeepingItemRenderer) (Object) player.firstPersonHandsAndItems();
    }

    /** 无本地玩家时的空实现：没有可保持的状态，当前物品为空。 */
    final class Empty implements KeepingItemRenderer {
        private static final Empty INSTANCE = new Empty();

        private Empty() {
        }

        @Override
        public void keep(ItemStack itemStack, long timeMs) {
        }

        @Override
        public ItemStack getCurrentItem() {
            return ItemStack.EMPTY;
        }
    }
}
