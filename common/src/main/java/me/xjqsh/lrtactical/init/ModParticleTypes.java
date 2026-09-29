package me.xjqsh.lrtactical.init;

import me.xjqsh.lrtactical.EquipmentMod;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * 粒子类型注册。
 *
 * <p>The type object is created with the class, but only {@link #init()} registers it;
 * {@code TaczRegistration} calls that while the particle type registry is open. Vanilla's
 * {@code SimpleParticleType} constructor is protected: NeoForge and Forge make it public, and
 * {@code tacz.accesswidener} does the same for Fabric and Quilt.
 *
 * <p>参数 {@code alwaysSpawn = true}：无视客户端「粒子数量」图形设置。
 * 烟雾弹是战术道具，遮蔽效果直接影响玩法平衡，
 * 不能因为对方把粒子调到「最少」就看得一清二楚，故与上游一致取 true。
 */
public final class ModParticleTypes {
    public static final SimpleParticleType SMOKE_CLOUD = new SimpleParticleType(true);

    private ModParticleTypes() {
    }

    public static void init() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "smoke_cloud"), SMOKE_CLOUD);
    }
}
