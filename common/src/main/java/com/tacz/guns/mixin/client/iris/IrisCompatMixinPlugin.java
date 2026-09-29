package com.tacz.guns.mixin.client.iris;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Applies TACZ Iris-internal mixins only when Iris is actually present. */
public final class IrisCompatMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("tacz");
    /**
     * Iris 主类的资源路径（Iris 1.11.2+26.2 的 Fabric 与 NeoForge jar 均已核对存在）。
     *
     * <p>mixin 配置插件在 Forge/NeoForge 上先于 mod 加载运行，此时不能经 {@code Platform}
     * 查询 mod 列表；这里只探测类资源是否存在，不加载任何 Iris 类。</p>
     */
    private static final String IRIS_CLASS_RESOURCE = "net/irisshaders/iris/Iris.class";
    private static boolean loggedDecision;
    private boolean irisPresent;

    @Override
    public void onLoad(String mixinPackage) {
        irisPresent = IrisCompatMixinPlugin.class.getClassLoader().getResource(IRIS_CLASS_RESOURCE) != null;
        LOGGER.info("[TACZ Scope] Iris compat mixin config loaded: package={}, irisLoaded={}",
                mixinPackage, irisPresent);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean apply = irisPresent;
        if (!loggedDecision) {
            loggedDecision = true;
            LOGGER.info("[TACZ Scope] Iris compat mixin decision: apply={}, firstMixin={}, firstTarget={}",
                    apply, mixinClassName, targetClassName);
        }
        return apply;
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
