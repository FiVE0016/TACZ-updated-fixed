package me.xjqsh.lrtactical.mixin.accessor;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Vanilla's own critical hit check, for LRTactical melee attacks. It stays private on NeoForge and Forge too, so it
 * can't go into {@code tacz.accesswidener}.
 */
@Mixin(Player.class)
public interface PlayerInvoker {
    @Invoker("canCriticalAttack")
    boolean lrtactical$canCriticalAttack(Entity target);
}
