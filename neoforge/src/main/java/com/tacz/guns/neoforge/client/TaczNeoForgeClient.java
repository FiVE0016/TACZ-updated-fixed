package com.tacz.guns.neoforge.client;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.gui.preview.GunPreviewRenderState;
import com.tacz.guns.client.gui.preview.GunPreviewRenderer;
import com.tacz.guns.client.init.ClientSetupEvent;
import com.tacz.guns.client.init.ModEntitiesRender;
import com.tacz.guns.client.init.TaczClientEvents;
import com.tacz.guns.init.CompatRegistry;
import net.minecraft.client.Minecraft;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@Mod(value = GunMod.MOD_ID, dist = Dist.CLIENT)
public final class TaczNeoForgeClient {
    public TaczNeoForgeClient(IEventBus modBus, ModContainer container) {
        // Fired before the first resource load, which decodes items/*.json.
        modBus.addListener(RegisterItemModelsEvent.class, event -> ClientSetupEvent.registerItemModelTypes());
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> ClientSetupEvent.keyMappings().forEach(event::register));
        modBus.addListener(RegisterClientTooltipComponentFactoriesEvent.class, event ->
                ClientSetupEvent.tooltipComponentFactories().forEach(factory -> registerTooltipFactory(event, factory)));
        modBus.addListener(RegisterGuiLayersEvent.class, event -> {
            for (ClientSetupEvent.HudLayer layer : ClientSetupEvent.hudLayers()) {
                event.registerAboveAll(layer.id(), (graphics, deltaTracker) -> layer.renderer().accept(graphics, deltaTracker));
            }
        });
        modBus.addListener(AddClientReloadListenersEvent.class, event -> ClientSetupEvent.registerClientReloadListeners(
                listener -> event.addListener(listener.getReloadListenerId(), listener)));
        modBus.addListener(RegisterMenuScreensEvent.class, event -> ClientSetupEvent.registerMenuScreens(new ClientSetupEvent.MenuScreenRegistrar() {
            @Override
            public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(MenuType<? extends M> type, MenuScreens.ScreenConstructor<M, U> constructor) {
                event.register(type, constructor);
            }
        }));
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, TaczNeoForgeClient::registerRenderers);
        modBus.addListener(RegisterParticleProvidersEvent.class, event -> ClientSetupEvent.registerParticleProviders(new ClientSetupEvent.ParticleRegistrar() {
            @Override
            public <T extends ParticleOptions> void registerSpecial(ParticleType<T> type, ParticleProvider<T> provider) {
                event.registerSpecial(type, provider);
            }

            @Override
            public <T extends ParticleOptions> void registerSpriteSet(ParticleType<T> type, ParticleResources.SpriteParticleRegistration<T> registration) {
                event.registerSpriteSet(type, registration);
            }
        }));
        // The gun smith table's rotating preview model is drawn through a picture-in-picture renderer.
        modBus.addListener(RegisterPictureInPictureRenderersEvent.class, event -> event.register(GunPreviewRenderState.class, GunPreviewRenderer::new));
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(ClientSetupEvent::registerBuiltinItemRenderers));
        modBus.addListener(FMLLoadCompleteEvent.class, event -> event.enqueueWork(() -> TaczClientEvents.onClientStarted(Minecraft.getInstance())));

        // Registered unconditionally: the factory falls back to TACZ's own screen when Cloth
        // Config is absent, so the scope/PIP toggles are always reachable from the mod list.
        container.registerExtensionPoint(IConfigScreenFactory.class, new ClothConfigScreenFactory());

        TaczClientEvents.registerListeners();
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Pre.class, event -> TaczClientEvents.onClientTickStart(Minecraft.getInstance()));
        bus.addListener(ClientTickEvent.Post.class, event -> TaczClientEvents.onClientTickEnd(Minecraft.getInstance()));
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> TaczClientEvents.onDisconnect(event.getConnection()));
        bus.addListener(ItemTooltipEvent.class, event -> TaczClientEvents.onItemTooltip(event.getItemStack(), event.getFlags(), event.getToolTip()));
        // NeoForge computes the FOV modifier through this event instead of the vanilla code TACZ hooks on Fabric.
        bus.addListener(ComputeFovModifierEvent.class, event -> {
            Float override = TaczClientEvents.computeFovModifierOverride(event.getPlayer(), event.getFovModifier());
            if (override != null) {
                event.setNewFovModifier(override);
            }
        });
    }

    private static <T extends TooltipComponent> void registerTooltipFactory(RegisterClientTooltipComponentFactoriesEvent event,
                                                                          ClientSetupEvent.TooltipFactory<T> factory) {
        event.register(factory.type(), factory.factory());
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        ModEntitiesRender.registerEntityRenderers(new ModEntitiesRender.Registrar() {
            @Override
            public <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
                event.registerEntityRenderer(type, provider);
            }

            @Override
            public <T extends BlockEntity, S extends BlockEntityRenderState> void registerBlockEntityRenderer(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider) {
                event.registerBlockEntityRenderer(type, provider);
            }
        });
    }
}
