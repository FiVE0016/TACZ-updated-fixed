package com.tacz.guns.mixin.client.voxy;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Applies the Voxy scope-pass mixins only when Voxy is actually installed. */
public final class VoxyCompatMixinPlugin implements IMixinConfigPlugin {
    /**
     * Resource path of Voxy's render system, one of this config's targets.
     *
     * <p>Mixin config plugins run before Forge/NeoForge load mods, so {@code Platform} cannot be asked
     * yet; this only probes for the class file and loads no Voxy class.</p>
     */
    private static final String VOXY_CLASS_RESOURCE = "me/cortex/voxy/client/core/VoxyRenderSystem.class";
    private boolean voxyPresent;

    @Override
    public void onLoad(String mixinPackage) {
        voxyPresent = VoxyCompatMixinPlugin.class.getClassLoader().getResource(VOXY_CLASS_RESOURCE) != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return voxyPresent;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
