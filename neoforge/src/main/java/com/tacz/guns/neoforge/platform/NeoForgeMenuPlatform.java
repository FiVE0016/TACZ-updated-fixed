package com.tacz.guns.neoforge.platform;

import com.tacz.guns.platform.MenuPlatform;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

public final class NeoForgeMenuPlatform implements MenuPlatform {
    @Override
    public <T extends AbstractContainerMenu, D> MenuType<T> createMenuType(MenuFactory<T, D> factory,
                                                                           StreamCodec<? super RegistryFriendlyByteBuf, D> dataCodec) {
        return IMenuTypeExtension.create((containerId, inventory, buf) -> factory.create(containerId, inventory, dataCodec.decode(buf)));
    }

    @Override
    public <D> void openMenu(ServerPlayer player, MenuProvider provider,
                             StreamCodec<? super RegistryFriendlyByteBuf, D> dataCodec, D data) {
        player.openMenu(provider, buf -> dataCodec.encode(buf, data));
    }
}
