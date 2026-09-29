package com.tacz.guns.init;

import cn.sh1rocu.tacz.api.event.EntityJoinLevelEvent;
import cn.sh1rocu.tacz.api.event.LivingHurtEvent;
import cn.sh1rocu.tacz.api.event.LivingKnockBackEvent;
import cn.sh1rocu.tacz.api.event.PlayerEvent;
import cn.sh1rocu.tacz.api.event.PlayerTickEvent;
import com.mojang.brigadier.CommandDispatcher;
import com.tacz.guns.api.event.bus.Event;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.command.RootCommand;
import com.tacz.guns.config.TaczConfigs;
import com.tacz.guns.event.EntityDamageEvent;
import com.tacz.guns.event.HitboxHelperEvent;
import com.tacz.guns.event.KnockbackChange;
import com.tacz.guns.event.PlayerRespawnEvent;
import com.tacz.guns.event.PreventGunClick;
import com.tacz.guns.event.ServerTickEvent;
import com.tacz.guns.event.SyncBaseTimestamp;
import com.tacz.guns.event.SyncedEntityDataEvent;
import com.tacz.guns.event.TravelToDimensionEvent;
import com.tacz.guns.event.ammo.BellRing;
import com.tacz.guns.event.ammo.DestroyGlassBlock;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageSyncServerConfig;
import com.tacz.guns.resource.CommonAssetsManager;
import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.event.MeleeAttackHandler;
import me.xjqsh.lrtactical.init.ModCapabilities;
import me.xjqsh.lrtactical.network.LrNetworkHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;

/**
 * Server and common hooks. {@link #registerListeners()} subscribes to TACZ's own events, which mixins fire
 * the same way on every loader; the remaining methods are called by each loader's own events.
 */
public final class TaczCommonEvents {
    private static volatile WeakReference<MinecraftServer> currentServer = new WeakReference<>(null);

    private TaczCommonEvents() {
    }

    public static void registerListeners() {
        CapabilityRegistry.init();

        AmmoHitBlockEvent.CALLBACK.register(BellRing::onAmmoHitBlock);
        AmmoHitBlockEvent.CALLBACK.register(DestroyGlassBlock::onAmmoHitBlock);

        LivingHurtEvent.CALLBACK.register(Event.LOW, EntityDamageEvent::onLivingHurt);

        PlayerTickEvent.END.register(HitboxHelperEvent::onPlayerTick);
        PlayerEvent.LOGGED_OUT.register(HitboxHelperEvent::onPlayerLoggedOut);

        LivingKnockBackEvent.CALLBACK.register(KnockbackChange::onKnockback);

        EntityJoinLevelEvent.CALLBACK.register(SyncBaseTimestamp::onPlayerJoinWorld);
        EntityJoinLevelEvent.CALLBACK.register(SyncedEntityDataEvent::onPlayerJoinWorld);

        // The bundled LRTactical add-on (throwables, melee weapons, consumables).
        EquipmentMod.init();
    }

    /**
     * @return the running server (integrated or dedicated), or {@code null} when none is running
     */
    @Nullable
    public static MinecraftServer getServer() {
        return currentServer.get();
    }

    public static void onServerStarting(MinecraftServer server) {
        currentServer = new WeakReference<>(server);
        TaczConfigs.onServerStarting(server);
    }

    public static void onServerStarted(MinecraftServer server) {
        CommonRegistry.onLoadComplete();
    }

    public static void onServerStopped(MinecraftServer server) {
        CommonAssetsManager.onServerStopped(server);
        TaczConfigs.onServerStopped();
        currentServer = new WeakReference<>(null);
    }

    /**
     * At the start of every server tick. Forge's tick event fired for both phases and TACZ's handler never
     * checked which, so the task ticker runs at the start and at the end of each tick.
     */
    public static void onServerTickStart(MinecraftServer server) {
        ServerTickEvent.onServerTick(server);
    }

    /**
     * At the end of every server tick.
     */
    public static void onServerTickEnd(MinecraftServer server) {
        ServerTickEvent.onServerTick(server);
        SyncedEntityDataEvent.onServerTick(server);
    }

    /**
     * Once a player has joined the server.
     */
    public static void onPlayerLoggedIn(ServerPlayer player) {
        NetworkHandler.sendToClientPlayer(new ServerMessageSyncServerConfig(TaczConfigs.serializeServerConfig()), player);
    }

    /**
     * When data pack contents are sent to a player (on join and after {@code /reload}).
     */
    public static void onDatapackSync(ServerPlayer player, boolean joined) {
        CommonAssetsManager.OnDatapackSync(player, joined);
        // LRTactical's indexes are only loaded on the server; remote clients receive them here.
        LrNetworkHandler.syncToPlayer(player, joined);
    }

    public static void onTagsLoaded(RegistryAccess registries, boolean client) {
        CommonAssetsManager.onReload(registries, client);
    }

    /**
     * When a player is recreated after death or leaving the End, before the new player is added.
     */
    public static void onPlayerClone(ServerPlayer original, ServerPlayer player, boolean alive) {
        SyncedEntityDataEvent.onPlayerClone(original, player, alive);
    }

    /**
     * After a player has respawned.
     */
    public static void onPlayerRespawn(ServerPlayer player, boolean alive) {
        PlayerRespawnEvent.onPlayerRespawn(player, alive);
        ModCapabilities.onRespawn(player);
    }

    public static void onStartTracking(Entity entity, ServerPlayer player) {
        SyncedEntityDataEvent.onStartTracking(entity, player);
    }

    /**
     * After a non-player entity was copied into another dimension.
     */
    public static void onEntityChangeDimension(Entity originalEntity, Entity newEntity, ServerLevel origin, ServerLevel destination) {
        TravelToDimensionEvent.onTravelToDimension(originalEntity, newEntity, origin, destination);
    }

    /**
     * After a player moved to another dimension.
     */
    public static void onPlayerChangeDimension(ServerPlayer player, ServerLevel origin, ServerLevel destination) {
        TravelToDimensionEvent.onPlayerTravelToDimension(player, origin, destination);
    }

    /**
     * @return whether the loader should cancel the player starting to break (left-clicking) a block
     */
    public static boolean shouldCancelLeftClickBlock(Player player, Level level, InteractionHand hand, BlockPos pos, Direction direction) {
        return PreventGunClick.onLeftClickBlock(player, level, hand, pos, direction) != InteractionResult.PASS;
    }

    /**
     * Called on both sides when a player attacks (left-clicks) an entity, before vanilla deals any damage.
     * LRTactical melee weapons settle their attacks through their own packet, so the vanilla attack is cancelled
     * for them; where the loader allows it, the client should then not send the vanilla attack packet either.
     *
     * @return whether the loader should cancel the attack
     */
    public static boolean shouldCancelAttackEntity(Player player, Level level, InteractionHand hand, Entity target) {
        return MeleeAttackHandler.onAttackEntity(player, level, hand, target, null) != InteractionResult.PASS;
    }

    /**
     * Fires TACZ's {@link LivingHurtEvent} for loaders that replace the vanilla damage code its hurt mixin hooks
     * (NeoForge). Call it where the loader lets mods change incoming damage before armor.
     *
     * @return the damage to deal; zero or less means the hurt should be cancelled
     */
    public static float onLivingHurt(LivingEntity entity, DamageSource source, float amount) {
        LivingHurtEvent event = new LivingHurtEvent(entity, source, amount);
        LivingHurtEvent.CALLBACK.invoker().onLivingHurt(event);
        return event.isCanceled() ? 0 : event.getAmount();
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        RootCommand.register(dispatcher);
    }

    /**
     * Adds the server-side gun pack reload listeners for a data reload, in the order they must run.
     */
    public static void registerServerReloadListeners(ReloadableServerResources serverResources, Consumer<PreparableReloadListener> register) {
        CommonAssetsManager.onAddReloadListeners(serverResources, register);
        EquipmentMod.registerServerReloadListeners(register);
    }
}
