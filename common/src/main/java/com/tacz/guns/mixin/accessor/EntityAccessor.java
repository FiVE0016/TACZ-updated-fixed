package com.tacz.guns.mixin.accessor;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code Entity#invulnerableTime} 的写入口。
 *
 * <p>26.2 时这个字段由 NeoForge/Forge 各自的 access transformer 放开，所以
 * {@code tacz.accesswidener} 只需为 Fabric/Quilt 补同一份访问权（该文件开头的
 * 约定就是「只放 NeoForge 与 Forge 本来就已放开的成员」）。26.3 的 NeoForge AT
 * 不再放开它，于是 NeoForge 那边重编 common 源码时报 private access ——
 * accessWidener 是 Loom 的概念，moddev/ForgeGradle 都不读它。
 *
 * <p>照 accesswidener 里写明的后备路线走：改用 accessor mixin。一个文件、四个
 * 加载器行为一致，也不必给 NeoForge 和 Forge 各加一份 AT 配置。
 *
 * <p>全部调用点都是「清零」——把原版 10 tick 的受伤无敌帧清掉，好让子弹/爆炸/
 * 近战连招的自定义伤害不被静默吞掉，所以这里只需要 setter。
 */
@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("invulnerableTime")
    void tacz$setInvulnerableTime(int invulnerableTime);
}
