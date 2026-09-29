package com.tacz.guns.fabric;

import com.tacz.guns.client.gui.preview.GunPreviewRenderer;
import com.tacz.guns.client.init.ClientSetupEvent;
import com.tacz.guns.client.init.ModEntitiesRender;
import com.tacz.guns.client.init.TaczClientEvents;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.platform.IdentifiableReloadListener;
import com.tacz.guns.platform.NetworkPlatform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class TaczFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Must precede the first client resource load, which decodes items/*.json.
        ClientSetupEvent.registerItemModelTypes();

        NetworkHandler.payloads().forEach(TaczFabricClient::registerClientReceiver);

        ClientSetupEvent.keyMappings().forEach(KeyMappingHelper::registerKeyMapping);
        ClientTooltipComponentCallback.EVENT.register(ClientSetupEvent::createTooltipComponent);
        for (ClientSetupEvent.HudLayer layer : ClientSetupEvent.hudLayers()) {
            HudElementRegistry.addLast(layer.id(), (graphics, deltaTracker) -> layer.renderer().accept(graphics, deltaTracker));
        }
        ClientSetupEvent.registerClientReloadListeners(listener ->
                ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new FabricReloadListener(listener)));

        ClientSetupEvent.registerMenuScreens(new ClientSetupEvent.MenuScreenRegistrar() {
            @Override
            public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(MenuType<? extends M> type, MenuScreens.ScreenConstructor<M, U> constructor) {
                MenuScreens.register(type, constructor);
            }
        });
        ModEntitiesRender.registerEntityRenderers(new ModEntitiesRender.Registrar() {
            @Override
            public <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
                EntityRendererRegistry.register(type, provider);
            }

            @Override
            public <T extends BlockEntity, S extends BlockEntityRenderState> void registerBlockEntityRenderer(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider) {
                BlockEntityRendererRegistry.register(type, provider);
            }
        });
        ClientSetupEvent.registerParticleProviders(new ClientSetupEvent.ParticleRegistrar() {
            @Override
            public <T extends ParticleOptions> void registerSpecial(ParticleType<T> type, ParticleProvider<T> provider) {
                ParticleProviderRegistry.getInstance().register(type, provider);
            }

            @Override
            public <T extends ParticleOptions> void registerSpriteSet(ParticleType<T> type, ParticleResources.SpriteParticleRegistration<T> registration) {
                ParticleProviderRegistry.getInstance().register(type, sprites -> registration.create(sprites));
            }
        });
        ClientSetupEvent.registerBuiltinItemRenderers();
        // The gun smith table's rotating preview model is drawn through a picture-in-picture renderer.
        PictureInPictureRendererRegistry.register(context -> new GunPreviewRenderer());

        TaczClientEvents.registerListeners();
        ClientLifecycleEvents.CLIENT_STARTED.register(TaczClientEvents::onClientStarted);
        ClientTickEvents.START_CLIENT_TICK.register(TaczClientEvents::onClientTickStart);
        ClientTickEvents.END_CLIENT_TICK.register(TaczClientEvents::onClientTickEnd);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TaczClientEvents.onDisconnect(handler.getConnection()));
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, flag, lines) -> TaczClientEvents.onItemTooltip(stack, flag, lines));
    }

    private static <T extends CustomPacketPayload> void registerClientReceiver(NetworkPlatform.PayloadSpec<T> spec) {
        if (spec.direction() == NetworkPlatform.Direction.CLIENTBOUND) {
            ClientPlayNetworking.registerGlobalReceiver(spec.type(), (payload, context) -> spec.handler().handle(payload, null));
        }
    }

    /**
     * Adapts TACZ's loader-neutral reload listener to Fabric's resource loader, which requires an id.
     */
    private record FabricReloadListener(IdentifiableReloadListener delegate) implements IdentifiableResourceReloadListener {
        @Override
        public Identifier getFabricId() {
            return delegate.getReloadListenerId();
        }

        @Override
        public void prepareSharedState(PreparableReloadListener.SharedState currentReload) {
            delegate.prepareSharedState(currentReload);
        }

        @Override
        public CompletableFuture<Void> reload(PreparableReloadListener.SharedState currentReload, Executor taskExecutor,
                                              PreparableReloadListener.PreparationBarrier preparationBarrier, Executor reloadExecutor) {
            return delegate.reload(currentReload, taskExecutor, preparationBarrier, reloadExecutor);
        }

        @Override
        public String getName() {
            return delegate.getName();
        }
    }
}
