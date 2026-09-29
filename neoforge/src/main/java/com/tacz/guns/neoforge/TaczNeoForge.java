package com.tacz.guns.neoforge;

import com.tacz.guns.GunMod;
import com.tacz.guns.init.TaczCommonEvents;
import com.tacz.guns.init.TaczRegistration;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.platform.IdentifiableReloadListener;
import com.tacz.guns.platform.NetworkPlatform;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.DefaultDataComponentsBoundEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * NeoForge entrypoint (both sides): registers content in NeoForge's registry events and wires TACZ's hooks to
 * NeoForge events. Client-only wiring lives in {@link com.tacz.guns.neoforge.client.TaczNeoForgeClient}.
 */
@Mod(GunMod.MOD_ID)
public final class TaczNeoForge {
    private static final String NETWORK_VERSION = "1";

    public TaczNeoForge(IEventBus modBus, ModContainer container) {
        GunMod.init();
        modBus.addListener(RegisterEvent.class, event -> TaczRegistration.register(event.getRegistryKey()));
        modBus.addListener(RegisterPayloadHandlersEvent.class, TaczNeoForge::registerPayloads);

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(ServerStartingEvent.class, event -> TaczCommonEvents.onServerStarting(event.getServer()));
        bus.addListener(ServerStartedEvent.class, event -> TaczCommonEvents.onServerStarted(event.getServer()));
        bus.addListener(ServerStoppedEvent.class, event -> TaczCommonEvents.onServerStopped(event.getServer()));
        bus.addListener(OnDatapackSyncEvent.class, event -> {
            ServerPlayer joined = event.getPlayer();
            if (joined != null) {
                TaczCommonEvents.onDatapackSync(joined, true);
            } else {
                event.getRelevantPlayers().forEach(player -> TaczCommonEvents.onDatapackSync(player, false));
            }
        });
        // NeoForge announces a server data load's tags before it binds that load's default item components, and
        // TACZ builds recipe result stacks once tags are in, so the server side waits for the components instead.
        RegistryAccess[] pendingServerTags = {null};
        // NeoForge 26.3 dropped TagsUpdatedEvent.UpdateCause (and getUpdateCause) in favour of two
        // event subclasses, so the branch becomes two listeners instead of one plus a cause check.
        bus.addListener(TagsUpdatedEvent.ClientPacketReceived.class,
                event -> TaczCommonEvents.onTagsLoaded(event.getRegistries(), true));
        bus.addListener(TagsUpdatedEvent.ServerDataLoad.class,
                event -> pendingServerTags[0] = event.getRegistries());
        bus.addListener(DefaultDataComponentsBoundEvent.class, event -> {
            RegistryAccess registries = pendingServerTags[0];
            if (registries != null) {
                pendingServerTags[0] = null;
                TaczCommonEvents.onTagsLoaded(registries, false);
            }
        });
        bus.addListener(AddServerReloadListenersEvent.class, TaczNeoForge::addServerReloadListeners);

        bus.addListener(ServerTickEvent.Pre.class, event -> TaczCommonEvents.onServerTickStart(event.getServer()));
        bus.addListener(ServerTickEvent.Post.class, event -> TaczCommonEvents.onServerTickEnd(event.getServer()));

        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                TaczCommonEvents.onPlayerLoggedIn(player);
            }
        });
        bus.addListener(PlayerEvent.Clone.class, event -> {
            if (event.getOriginal() instanceof ServerPlayer original && event.getEntity() instanceof ServerPlayer player) {
                TaczCommonEvents.onPlayerClone(original, player, !event.isWasDeath());
            }
        });
        bus.addListener(PlayerEvent.PlayerRespawnEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                TaczCommonEvents.onPlayerRespawn(player, event.isEndConquered());
            }
        });
        bus.addListener(PlayerEvent.StartTracking.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                TaczCommonEvents.onStartTracking(event.getTarget(), player);
            }
        });
        // Players are moved to the new level and get their own "changed" event; other entities are copied,
        // and NeoForge only announces that before the copy.
        bus.addListener(EntityTravelToDimensionEvent.class, event -> {
            Entity entity = event.getEntity();
            if (!(entity instanceof ServerPlayer) && entity.level() instanceof ServerLevel origin) {
                ServerLevel destination = origin.getServer().getLevel(event.getDimension());
                if (destination != null) {
                    TaczCommonEvents.onEntityChangeDimension(entity, entity, origin, destination);
                }
            }
        });
        bus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                MinecraftServer server = player.level().getServer();
                ServerLevel origin = server.getLevel(event.getFrom());
                ServerLevel destination = server.getLevel(event.getTo());
                if (origin != null && destination != null) {
                    TaczCommonEvents.onPlayerChangeDimension(player, origin, destination);
                }
            }
        });

        bus.addListener(PlayerInteractEvent.LeftClickBlock.class, event -> {
            if (TaczCommonEvents.shouldCancelLeftClickBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getPos(), event.getFace())) {
                event.setCanceled(true);
            }
        });
        // NeoForge rewrites LivingEntity#actuallyHurt, so TACZ's hurt mixin is skipped here; this event is NeoForge's
        // point for changing incoming damage before armor.
        bus.addListener(LivingIncomingDamageEvent.class, event -> {
            float amount = TaczCommonEvents.onLivingHurt(event.getEntity(), event.getSource(), event.getAmount());
            if (amount <= 0) {
                event.setCanceled(true);
            } else {
                event.setAmount(amount);
            }
        });
        // Fired inside Player#attack on both sides; cancelling it on the server stops the vanilla damage.
        bus.addListener(AttackEntityEvent.class, event -> {
            Player player = event.getEntity();
            if (TaczCommonEvents.shouldCancelAttackEntity(player, player.level(), InteractionHand.MAIN_HAND, event.getTarget())) {
                event.setCanceled(true);
            }
        });
        bus.addListener(RegisterCommandsEvent.class, event -> TaczCommonEvents.registerCommands(event.getDispatcher()));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        NetworkHandler.payloads().forEach(spec -> registerPayload(registrar, spec));
    }

    private static <T extends CustomPacketPayload> void registerPayload(PayloadRegistrar registrar, NetworkPlatform.PayloadSpec<T> spec) {
        // NeoForge runs payload handlers on the main thread unless told otherwise, as TACZ's handlers expect.
        if (spec.direction() == NetworkPlatform.Direction.SERVERBOUND) {
            registrar.playToServer(spec.type(), spec.codec(), (payload, context) -> spec.handler().handle(payload, (ServerPlayer) context.player()));
        } else {
            registrar.playToClient(spec.type(), spec.codec(), (payload, context) -> spec.handler().handle(payload, null));
        }
    }

    private static void addServerReloadListeners(AddServerReloadListenersEvent event) {
        // NeoForge needs a key per listener; unkeyed listeners run after vanilla's in registration order.
        int[] unnamed = {0};
        TaczCommonEvents.registerServerReloadListeners(event.getServerResources(), listener -> {
            Identifier key = listener instanceof IdentifiableReloadListener identifiable
                    ? identifiable.getReloadListenerId()
                    : Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "server_reload_listener_" + unnamed[0]++);
            event.addListener(key, listener);
        });
    }
}
