package com.tacz.guns.forge.client;

import com.tacz.guns.client.gui.preview.GunPreviewRenderer;
import com.tacz.guns.client.init.ClientSetupEvent;
import com.tacz.guns.client.init.ModEntitiesRender;
import com.tacz.guns.client.init.TaczClientEvents;
import com.tacz.guns.client.gui.compat.FallbackConfigScreen;
import com.tacz.guns.compat.cloth.MenuIntegration;
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
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterPictureInPictureRendererEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge client wiring, called from {@link com.tacz.guns.forge.TaczForge} on the physical client only.
 */
public final class TaczForgeClient {
    private TaczForgeClient() {
    }

    public static void init(FMLJavaModLoadingContext context) {
        BusGroup modBus = context.getModBusGroup();
        // Forge has no item model registration event; this must precede the first resource load,
        // which decodes items/*.json.
        ClientSetupEvent.registerItemModelTypes();

        RegisterKeyMappingsEvent.BUS.addListener(event -> ClientSetupEvent.keyMappings().forEach(event::register));
        RegisterClientTooltipComponentFactoriesEvent.BUS.addListener(event ->
                ClientSetupEvent.tooltipComponentFactories().forEach(factory -> registerTooltipFactory(event, factory)));
        AddGuiOverlayLayersEvent.BUS.addListener(event -> {
            ForgeLayeredDraw draw = event.getLayeredDraw();
            for (ClientSetupEvent.HudLayer layer : ClientSetupEvent.hudLayers()) {
                // The pre-sleep stack holds the HUD proper, so these layers hide with it (F1).
                draw.add(ForgeLayeredDraw.PRE_SLEEP_STACK, layer.id(), (graphics, deltaTracker) -> layer.renderer().accept(graphics, deltaTracker));
            }
        });
        RegisterClientReloadListenersEvent.BUS.addListener(event -> ClientSetupEvent.registerClientReloadListeners(event::registerReloadListener));
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(TaczForgeClient::registerRenderers);
        RegisterParticleProvidersEvent.BUS.addListener(event -> ClientSetupEvent.registerParticleProviders(new ClientSetupEvent.ParticleRegistrar() {
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
        RegisterPictureInPictureRendererEvent.BUS.addListener(event -> event.register(new GunPreviewRenderer()));
        FMLClientSetupEvent.getBus(modBus).addListener(event -> event.enqueueWork(() -> {
            ClientSetupEvent.registerMenuScreens(new ClientSetupEvent.MenuScreenRegistrar() {
                @Override
                public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(MenuType<? extends M> type, MenuScreens.ScreenConstructor<M, U> constructor) {
                    MenuScreens.register(type, constructor);
                }
            });
            ClientSetupEvent.registerBuiltinItemRenderers();
        }));
        FMLLoadCompleteEvent.getBus(modBus).addListener(event -> event.enqueueWork(() -> TaczClientEvents.onClientStarted(Minecraft.getInstance())));

        if (ModList.isLoaded(CompatRegistry.CLOTH_CONFIG)) {
            MinecraftForge.registerConfigScreen(parent -> MenuIntegration.getConfigBuilder().setParentScreen(parent).build());
        } else {
            // Cloth Config has no Forge 26.3 build, so without this the mod list's Config button
            // would stay greyed out and the scope/PIP toggles would be unreachable in game.
            MinecraftForge.registerConfigScreen(FallbackConfigScreen::new);
        }

        TaczClientEvents.registerListeners();
        TickEvent.ClientTickEvent.Pre.BUS.addListener(event -> TaczClientEvents.onClientTickStart(Minecraft.getInstance()));
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> TaczClientEvents.onClientTickEnd(Minecraft.getInstance()));
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> TaczClientEvents.onDisconnect(event.getConnection()));
        ItemTooltipEvent.BUS.addListener(event -> TaczClientEvents.onItemTooltip(event.getItemStack(), event.getFlags(), event.getToolTip()));
        // Forge computes the FOV modifier through this event instead of the vanilla code TACZ hooks on Fabric.
        ComputeFovModifierEvent.BUS.addListener(event -> {
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
