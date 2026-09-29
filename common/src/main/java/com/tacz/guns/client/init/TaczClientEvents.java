package com.tacz.guns.client.init;

import cn.sh1rocu.simplebedrockmodel.api.event.RenderTickEvent;
import cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent;
import cn.sh1rocu.tacz.api.event.ClientPlayerNetworkEvent;
import cn.sh1rocu.tacz.api.event.ComputeFovModifierEvent;
import cn.sh1rocu.tacz.api.event.InputEvent;
import cn.sh1rocu.tacz.api.event.PlayerEvent;
import cn.sh1rocu.tacz.api.event.PlayerTickEvent;
import cn.sh1rocu.tacz.api.event.RenderLivingEvent;
import cn.sh1rocu.tacz.api.event.TextureStitchEvent;
import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.api.client.event.RenderItemInHandBobEvent;
import com.tacz.guns.api.client.event.SwapItemWithOffHand;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.compat.RecipeViewerReloadBridge;
import com.tacz.guns.client.event.CameraSetupEvent;
import com.tacz.guns.client.event.ClientHitMark;
import com.tacz.guns.client.event.ClientPreventGunClick;
import com.tacz.guns.client.event.CommonNetworkCacheEvent;
import com.tacz.guns.client.event.FirstPersonRenderGunEvent;
import com.tacz.guns.client.event.InventoryEvent;
import com.tacz.guns.client.event.PlayerEnterWorld;
import com.tacz.guns.client.event.PlayerHurtByGunEvent;
import com.tacz.guns.client.event.RefreshClonePlayerDataEvent;
import com.tacz.guns.client.event.ReloadResourceEvent;
import com.tacz.guns.client.event.RenderCrosshairEvent;
import com.tacz.guns.client.event.RenderHeadShotAABB;
import com.tacz.guns.client.event.TickAnimationEvent;
import com.tacz.guns.client.event.TooltipEvent;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.input.ConfigKey;
import com.tacz.guns.client.input.CrawlKey;
import com.tacz.guns.client.input.FireSelectKey;
import com.tacz.guns.client.input.InspectKey;
import com.tacz.guns.client.input.InteractKey;
import com.tacz.guns.client.input.MeleeKey;
import com.tacz.guns.client.input.RefitKey;
import com.tacz.guns.client.input.ReloadKey;
import com.tacz.guns.client.input.ShootKey;
import com.tacz.guns.client.input.ZoomKey;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.init.CommonRegistry;
import me.xjqsh.lrtactical.client.audio.DeafenState;
import me.xjqsh.lrtactical.client.event.LrTickAnimationEvent;
import me.xjqsh.lrtactical.client.input.MeleeAttackKeys;
import me.xjqsh.lrtactical.client.input.StuckUseRecovery;
import me.xjqsh.lrtactical.client.input.UsePressGate;
import me.xjqsh.lrtactical.init.ModCapabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Client-side hooks. {@link #registerListeners()} subscribes to TACZ's own events, which mixins fire the
 * same way on every loader; the remaining methods are called by each loader's client lifecycle events.
 */
public final class TaczClientEvents {
    private TaczClientEvents() {
    }

    public static void registerListeners() {
        RenderTickEvent.EVENT.register(RefitTransform::tickInterpolation);
        // LRTactical: third-person animation and keyframe sounds; first person is advanced by its item renderers.
        RenderTickEvent.EVENT.register(LrTickAnimationEvent::tickAnimation);

        ViewportEvent.CAMERA.register(CameraSetupEvent::applyLevelCameraAnimation);
        BeforeRenderHandEvent.CALLBACK.register(CameraSetupEvent::applyItemInHandCameraAnimation);
        ViewportEvent.FOV.register(CameraSetupEvent::applyScopeMagnification);
        ViewportEvent.FOV.register(CameraSetupEvent::applyGunModelFovModifying);
        GunFireEvent.CALLBACK.register(CameraSetupEvent::initialCameraRecoil);
        ViewportEvent.CAMERA.register(CameraSetupEvent::applyCameraRecoil);
        ComputeFovModifierEvent.CALLBACK.register(CameraSetupEvent::onComputeMovementFov);

        EntityHurtByGunEvent.POST.register(ClientHitMark::onEntityHurt);
        EntityKillByGunEvent.CALLBACK.register(ClientHitMark::onEntityKill);

        InputEvent.InteractionKeyMappingTriggered.EVENT.register(ClientPreventGunClick::onClickInput);

        RenderItemInHandBobEvent.VIEW.register(FirstPersonRenderGunEvent::cancelItemInHandViewBobbing);
        GunFireEvent.CALLBACK.register(FirstPersonRenderGunEvent::onGunFire);

        SwapItemWithOffHand.CALLBACK.register(InventoryEvent::onPlayerSwapMainHand);
        ClientPlayerNetworkEvent.LOGGING_OUT.register(InventoryEvent::onPlayerLoggedOut);

        PlayerEvent.LOGGED_IN.register(PlayerEnterWorld::onPlayerEnterWorld);

        EntityHurtByGunEvent.POST.register(PlayerHurtByGunEvent::onPlayerHurtByGun);

        TextureStitchEvent.POST.register(ReloadResourceEvent::onTextureStitchEventPost);

        RenderTickEvent.EVENT.register(RenderCrosshairEvent::onRenderTick);

        RenderLivingEvent.POST.register(RenderHeadShotAABB::onRenderEntity);

        RenderTickEvent.EVENT.register(TickAnimationEvent::tickAnimation);

        // LRTactical melee attacks listen to the mouse buttons themselves, so a swing that hits nothing still
        // reaches the server (area attacks don't need a first target).
        InputEvent.MouseButton.Post.EVENT.register(MeleeAttackKeys::onMousePress);

        InputEvent.MouseButton.Post.EVENT.register(AimKey::onAimPress);

        InputEvent.Key.EVENT.register(ConfigKey::onOpenConfig);
        InputEvent.Key.EVENT.register(CrawlKey::onCrawlPress);

        InputEvent.Key.EVENT.register(FireSelectKey::onFireSelectKeyPress);
        InputEvent.MouseButton.Post.EVENT.register(FireSelectKey::onFireSelectMousePress);

        InputEvent.Key.EVENT.register(InspectKey::onInspectPress);

        InputEvent.Key.EVENT.register(InteractKey::onInteractKeyPress);
        InputEvent.MouseButton.Post.EVENT.register(InteractKey::onInteractMousePress);

        InputEvent.Key.EVENT.register(MeleeKey::onMeleeKeyPress);
        InputEvent.MouseButton.Post.EVENT.register(MeleeKey::onMeleeMousePress);

        InputEvent.Key.EVENT.register(RefitKey::onRefitPress);

        InputEvent.Key.EVENT.register(ReloadKey::onReloadPress);
        PlayerTickEvent.START.register(ReloadKey::autoReload);

        InputEvent.Key.EVENT.register(ZoomKey::onZoomKeyPress);
        InputEvent.MouseButton.Post.EVENT.register(ZoomKey::onZoomMousePress);
    }

    /**
     * Once the client has finished starting.
     */
    public static void onClientStarted(Minecraft minecraft) {
        CommonRegistry.onLoadComplete();
        ClientSetupEvent.onClientSetup();
    }

    /**
     * At the start of every client tick.
     */
    public static void onClientTickStart(Minecraft minecraft) {
        InventoryEvent.onPlayerChangeSelect(minecraft, false);
        RefreshClonePlayerDataEvent.onClientTick(minecraft);
        // LRTactical: drop melee and cooldown state tied to a replaced local player (respawn, dimension change).
        ModCapabilities.onClientPlayerTick(minecraft.player);
        // LRTactical: idle/walk/run input for melee animation state machines; TickAnimationEvent only handles guns.
        LrTickAnimationEvent.tickAnimation(minecraft);
        TickAnimationEvent.tickAnimation(minecraft);
        AimKey.onAimHoldingPreInput(minecraft);
        ShootKey.autoShoot(minecraft, false);
    }

    /**
     * At the end of every client tick.
     */
    public static void onClientTickEnd(Minecraft minecraft) {
        // LRTactical: keeps the ringing sound playing while the player is deafened by a flashbang.
        DeafenState.tick(minecraft);
        InventoryEvent.onPlayerChangeSelect(minecraft, true);
        // Remote gun-pack sync may finish after JEI's initial registration pass.
        RecipeViewerReloadBridge.tick(minecraft);
        LrTickAnimationEvent.tickAnimation(minecraft);
        // LRTactical: one item use per press. Must sample at the end of the tick, after entities have ticked, so a
        // use that just ended is seen before the next tick's key handling restarts it (see UsePressGate).
        UsePressGate.onClientTick(minecraft);
        // LRTactical: backs out of a use the server never started, which would otherwise never end.
        StuckUseRecovery.onClientTick(minecraft);
        TickAnimationEvent.tickAnimation(minecraft);
        AimKey.cancelAim(minecraft);
        AimKey.onAimHoldingPreInput(minecraft);
        ShootKey.autoShoot(minecraft, true);
        SoundPlayManager.onClientTick(minecraft);
    }

    /**
     * When the client disconnects from a server.
     *
     * @param connection the connection that was closed, when the loader still has it
     */
    public static void onDisconnect(@Nullable Connection connection) {
        CommonNetworkCacheEvent.onClientPlayerLoggingIn(connection);
    }

    /**
     * While an item tooltip is being built.
     */
    public static void onItemTooltip(ItemStack stack, TooltipFlag flag, List<Component> lines) {
        TooltipEvent.onTooltip(stack, flag, lines);
    }

    /**
     * For loaders whose own FOV modifier event replaces the vanilla code TACZ hooks on Fabric: fires TACZ's
     * {@link ComputeFovModifierEvent} for the unscaled modifier.
     *
     * @return the scaled modifier TACZ wants, or {@code null} when no TACZ listener changed it
     */
    @Nullable
    public static Float computeFovModifierOverride(Player player, float fovModifier) {
        ComputeFovModifierEvent event = new ComputeFovModifierEvent(player, fovModifier);
        float unchanged = event.getNewFovModifier();
        ComputeFovModifierEvent.CALLBACK.invoker().post(event);
        return event.getNewFovModifier() != unchanged ? event.getNewFovModifier() : null;
    }
}
