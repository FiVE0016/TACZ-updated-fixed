package com.tacz.guns.mixin.carryon;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies Carry On internals only when the optional mod is present. Mixin plugins run before any loader can
 * report which mods are loaded (NeoForge's mod list does not exist yet), so Carry On is recognised by a class it ships.
 */
public final class CarryOnCompatMixinPlugin implements IMixinConfigPlugin {
    private static final String CARRY_ON_CLASS = "tschipp/carryon/common/carry/PickupHandler.class";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return CarryOnCompatMixinPlugin.class.getClassLoader().getResource(CARRY_ON_CLASS) != null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
    }
}
