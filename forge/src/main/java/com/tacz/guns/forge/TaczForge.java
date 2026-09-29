package com.tacz.guns.forge;

import com.tacz.guns.GunMod;
import com.tacz.guns.forge.client.TaczForgeClient;
import com.tacz.guns.forge.network.ForgeNetworking;
import com.tacz.guns.init.TaczCommonEvents;
import com.tacz.guns.init.TaczRegistration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.RegisterEvent;

import java.util.function.Predicate;

/**
 * Forge entrypoint (both sides): registers content in Forge's registry events and wires TACZ's hooks to
 * Forge events. Client-only wiring lives in {@link TaczForgeClient}.
 */
@Mod(GunMod.MOD_ID)
public final class TaczForge {
    public TaczForge(FMLJavaModLoadingContext context) {
        BusGroup modBus = context.getModBusGroup();
        GunMod.init();
        RegisterEvent.getBus(modBus).addListener(event -> TaczRegistration.register(event.getRegistryKey()));
        ForgeNetworking.init();

        ServerStartingEvent.BUS.addListener(event -> TaczCommonEvents.onServerStarting(event.getServer()));
        ServerStartedEvent.BUS.addListener(event -> TaczCommonEvents.onServerStarted(event.getServer()));
        ServerStoppedEvent.BUS.addListener(event -> TaczCommonEvents.onServerStopped(event.getServer()));
        OnDatapackSyncEvent.BUS.addListener(event -> {
            ServerPlayer joined = event.getPlayer();
            if (joined != null) {
                TaczCommonEvents.onDatapackSync(joined, true);
            } else {
                event.getPlayers().forEach(player -> TaczCommonEvents.onDatapackSync(player, false));
            }
        });
        TagsUpdatedEvent.BUS.addListener(event -> TaczCommonEvents.onTagsLoaded(event.getRegistryAccess(),
                event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED));
        AddReloadListenerEvent.BUS.addListener(event -> TaczCommonEvents.registerServerReloadListeners(event.getServerResources(), event::addListener));

        TickEvent.ServerTickEvent.Pre.BUS.addListener(event -> TaczCommonEvents.onServerTickStart(event.server()));
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> TaczCommonEvents.onServerTickEnd(event.server()));

        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                TaczCommonEvents.onPlayerLoggedIn(player);
            }
        });
        PlayerEvent.Clone.BUS.addListener(event -> {
            if (event.getOriginal() instanceof ServerPlayer original && event.getEntity() instanceof ServerPlayer player) {
                TaczCommonEvents.onPlayerClone(original, player, !event.isWasDeath());
            }
        });
        PlayerEvent.PlayerRespawnEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                TaczCommonEvents.onPlayerRespawn(player, event.isEndConquered());
            }
        });
        PlayerEvent.StartTracking.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                TaczCommonEvents.onStartTracking(event.getTarget(), player);
            }
        });
        // Players are moved to the new level and get their own "changed" event; other entities are copied,
        // and Forge only announces that before the copy.
        EntityTravelToDimensionEvent.BUS.addListener(event -> {
            Entity entity = event.getEntity();
            if (!(entity instanceof ServerPlayer) && entity.level() instanceof ServerLevel origin) {
                ServerLevel destination = origin.getServer().getLevel(event.getDimension());
                if (destination != null) {
                    TaczCommonEvents.onEntityChangeDimension(entity, entity, origin, destination);
                }
            }
        });
        PlayerEvent.PlayerChangedDimensionEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                MinecraftServer server = player.level().getServer();
                ServerLevel origin = server.getLevel(event.getFrom());
                ServerLevel destination = server.getLevel(event.getTo());
                if (origin != null && destination != null) {
                    TaczCommonEvents.onPlayerChangeDimension(player, origin, destination);
                }
            }
        });

        // A predicate listener cancels the event by returning true.
        PlayerInteractEvent.LeftClickBlock.BUS.addListener((Predicate<PlayerInteractEvent.LeftClickBlock>) event ->
                TaczCommonEvents.shouldCancelLeftClickBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getPos(), event.getFace()));
        // Fired inside Player#attack on both sides; cancelling it on the server stops the vanilla damage.
        AttackEntityEvent.BUS.addListener((Predicate<AttackEntityEvent>) event ->
                TaczCommonEvents.shouldCancelAttackEntity(event.getEntity(), event.getEntity().level(), InteractionHand.MAIN_HAND, event.getTarget()));
        RegisterCommandsEvent.BUS.addListener(event -> TaczCommonEvents.registerCommands(event.getDispatcher()));

        if (FMLEnvironment.dist.isClient()) {
            TaczForgeClient.init(context);
        }
    }
}
