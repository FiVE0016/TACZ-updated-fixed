package cn.sh1rocu.tacz.util;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies the mixins in {@code cn.sh1rocu.tacz.mixin.compat.<mod>} only when that mod is installed, and skips the
 * few hook mixins that a loader's own patches make unusable.
 * Mixin plugins run before any loader can answer "is this mod loaded", so each mod or loader is recognised by a
 * class file it ships. Skipping here also keeps Mixin from logging lookups of the missing target classes.
 */
public class CompatMixinPlugin implements IMixinConfigPlugin {
    private static final String COMPAT_PACKAGE = "cn.sh1rocu.tacz.mixin.compat.";
    private static final Map<String, String> MARKER_CLASSES = Map.of(
            "punchy", "punchy/client/render/PunchyArmRenderer.class"
    );
    /**
     * Hook mixins that are skipped when the given class exists.
     */
    private static final Map<String, String> SKIP_WHEN_PRESENT = Map.of(
            // NeoForge rewrites LivingEntity#actuallyHurt; TaczNeoForge fires the hurt event from LivingIncomingDamageEvent.
            "cn.sh1rocu.tacz.mixin.common.LivingEntityHurtMixin", "net/neoforged/neoforge/common/damagesource/DamageContainer.class"
    );

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String skipMarker = SKIP_WHEN_PRESENT.get(mixinClassName);
        if (skipMarker != null) {
            return !isPresent(skipMarker);
        }
        if (!mixinClassName.startsWith(COMPAT_PACKAGE)) {
            return true;
        }
        int end = mixinClassName.indexOf('.', COMPAT_PACKAGE.length());
        String marker = end < 0 ? null : MARKER_CLASSES.get(mixinClassName.substring(COMPAT_PACKAGE.length(), end));
        return marker != null && isPresent(marker);
    }

    private static boolean isPresent(String classFile) {
        return CompatMixinPlugin.class.getClassLoader().getResource(classFile) != null;
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
