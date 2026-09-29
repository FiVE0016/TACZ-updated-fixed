package com.tacz.guns.client.init;

import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.block.entity.StatueBlockEntity;
import com.tacz.guns.block.entity.TargetBlockEntity;
import com.tacz.guns.client.renderer.block.GunSmithTableRenderer;
import com.tacz.guns.client.renderer.block.StatueRenderer;
import com.tacz.guns.client.renderer.block.TargetRenderer;
import com.tacz.guns.client.renderer.entity.EntityBulletRenderer;
import com.tacz.guns.client.renderer.entity.TargetMinecartRenderer;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.entity.TargetMinecart;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ModEntitiesRender {
    /**
     * Implemented by each loader with its own renderer registration API.
     */
    public interface Registrar {
        <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type, EntityRendererProvider<T> provider);

        <T extends BlockEntity, S extends BlockEntityRenderState> void registerBlockEntityRenderer(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider);
    }

    public static void registerEntityRenderers(Registrar registrar) {
        registrar.registerEntityRenderer(EntityKineticBullet.TYPE, EntityBulletRenderer::new);
        registrar.registerEntityRenderer(TargetMinecart.TYPE, TargetMinecartRenderer::new);
        // All three blocks return RenderShape.INVISIBLE, so registration is mandatory:
        // without these renderers, placed tables/targets/statues are functional but invisible.
        registrar.registerBlockEntityRenderer(GunSmithTableBlockEntity.TYPE, GunSmithTableRenderer::new);
        registrar.registerBlockEntityRenderer(TargetBlockEntity.TYPE, TargetRenderer::new);
        registrar.registerBlockEntityRenderer(StatueBlockEntity.TYPE, StatueRenderer::new);
        // LRTactical's throwables and effect cloud (its registration class shares this class's name).
        me.xjqsh.lrtactical.client.init.ModEntitiesRender.registerEntityRenderers(registrar);
    }
}
