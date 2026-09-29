package com.tacz.guns.platform;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * Menus whose client-side constructor needs extra data sent when the screen opens.
 */
public interface MenuPlatform {
    MenuPlatform INSTANCE = Services.load(MenuPlatform.class);

    <T extends AbstractContainerMenu, D> MenuType<T> createMenuType(MenuFactory<T, D> factory,
                                                                    StreamCodec<? super RegistryFriendlyByteBuf, D> dataCodec);

    <D> void openMenu(ServerPlayer player, MenuProvider provider,
                      StreamCodec<? super RegistryFriendlyByteBuf, D> dataCodec, D data);

    @FunctionalInterface
    interface MenuFactory<T extends AbstractContainerMenu, D> {
        T create(int containerId, Inventory inventory, D data);
    }
}
