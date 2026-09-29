package com.tacz.guns.forge.platform;

import com.tacz.guns.platform.MenuPlatform;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;

public final class ForgeMenuPlatform implements MenuPlatform {
    @Override
    public <T extends AbstractContainerMenu, D> MenuType<T> createMenuType(MenuFactory<T, D> factory,
                                                                           StreamCodec<? super RegistryFriendlyByteBuf, D> dataCodec) {
        return IForgeMenuType.create((containerId, inventory, buf) ->
                factory.create(containerId, inventory, dataCodec.decode(withRegistries(buf, inventory.player.registryAccess()))));
    }

    @Override
    public <D> void openMenu(ServerPlayer player, MenuProvider provider,
                             StreamCodec<? super RegistryFriendlyByteBuf, D> dataCodec, D data) {
        player.openMenu(provider, buf -> dataCodec.encode(withRegistries(buf, player.registryAccess()), data));
    }

    /**
     * Forge's menu data buffers are plain {@link FriendlyByteBuf}s; TACZ's codecs are written for registry-aware ones.
     */
    private static RegistryFriendlyByteBuf withRegistries(FriendlyByteBuf buf, RegistryAccess registryAccess) {
        return buf instanceof RegistryFriendlyByteBuf registryBuf ? registryBuf : new RegistryFriendlyByteBuf(buf, registryAccess);
    }
}
