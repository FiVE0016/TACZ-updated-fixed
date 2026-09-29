package com.tacz.guns.fabric.platform;

import com.tacz.guns.platform.MenuPlatform;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.Nullable;

public final class FabricMenuPlatform implements MenuPlatform {
    @Override
    public <T extends AbstractContainerMenu, D> MenuType<T> createMenuType(MenuFactory<T, D> factory,
                                                                           StreamCodec<? super RegistryFriendlyByteBuf, D> dataCodec) {
        return new ExtendedMenuType<>(factory::create, dataCodec);
    }

    @Override
    public <D> void openMenu(ServerPlayer player, MenuProvider provider,
                             StreamCodec<? super RegistryFriendlyByteBuf, D> dataCodec, D data) {
        player.openMenu(new ExtendedMenuProvider<D>() {
            @Override
            public D getScreenOpeningData(ServerPlayer serverPlayer) {
                return data;
            }

            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player menuPlayer) {
                return provider.createMenu(containerId, inventory, menuPlayer);
            }
        });
    }
}
