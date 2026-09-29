package com.tacz.guns.init;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

import java.util.List;

/**
 * Registers TACZ's game objects, and those of the bundled LRTactical add-on, one registry at a time.
 * <p>
 * Forge and NeoForge only accept new entries while the matching registration event runs (Forge
 * unlocks a single registry per event), and several objects capture a registry holder when they are
 * constructed, so each group is created and registered inside its own registry's window. Fabric and
 * Quilt have no such window and register everything at start-up with {@link #registerAll()}.
 * LRTactical's registration classes share their simple names with TACZ's, hence the qualified names.
 */
public final class TaczRegistration {
    /**
     * The registries TACZ adds to, ordered so that every entry's dependencies are registered first.
     */
    public static final List<ResourceKey<? extends Registry<?>>> REGISTRIES = List.of(
            Registries.SOUND_EVENT,
            Registries.ATTRIBUTE,
            Registries.PARTICLE_TYPE,
            Registries.MOB_EFFECT,
            Registries.BLOCK,
            Registries.BLOCK_ENTITY_TYPE,
            Registries.ITEM,
            Registries.ENTITY_TYPE,
            Registries.MENU,
            Registries.RECIPE_TYPE,
            Registries.RECIPE_SERIALIZER,
            Registries.CREATIVE_MODE_TAB
    );

    private TaczRegistration() {
    }

    /**
     * Registers TACZ's entries for one registry; does nothing for registries TACZ doesn't use.
     */
    public static void register(ResourceKey<? extends Registry<?>> registry) {
        if (registry.equals(Registries.SOUND_EVENT)) {
            ModSounds.init();
        } else if (registry.equals(Registries.ATTRIBUTE)) {
            ModAttributes.init();
        } else if (registry.equals(Registries.PARTICLE_TYPE)) {
            ModParticles.init();
            me.xjqsh.lrtactical.init.ModParticleTypes.init();
        } else if (registry.equals(Registries.MOB_EFFECT)) {
            me.xjqsh.lrtactical.init.ModEffects.init();
        } else if (registry.equals(Registries.BLOCK)) {
            ModBlocks.init();
        } else if (registry.equals(Registries.BLOCK_ENTITY_TYPE)) {
            ModBlocks.registerBlockEntities();
        } else if (registry.equals(Registries.ITEM)) {
            ModItems.init();
            me.xjqsh.lrtactical.init.ModItems.init();
        } else if (registry.equals(Registries.ENTITY_TYPE)) {
            ModEntities.init();
            me.xjqsh.lrtactical.init.ModEntities.init();
        } else if (registry.equals(Registries.MENU)) {
            ModContainer.init();
        } else if (registry.equals(Registries.RECIPE_TYPE)) {
            ModRecipe.registerTypes();
        } else if (registry.equals(Registries.RECIPE_SERIALIZER)) {
            ModRecipe.registerSerializers();
        } else if (registry.equals(Registries.CREATIVE_MODE_TAB)) {
            ModCreativeTabs.init();
            me.xjqsh.lrtactical.init.ModCreativeTabs.init();
        }
    }

    /**
     * Registers every entry immediately, in dependency order.
     */
    public static void registerAll() {
        REGISTRIES.forEach(TaczRegistration::register);
    }
}
