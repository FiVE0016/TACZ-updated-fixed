package com.tacz.guns.resource.manager;
import com.tacz.guns.platform.IdentifiableReloadListener;

import com.google.common.collect.Maps;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.vmlib.LuaLibrary;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luaj.vm2.*;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.jse.JseBaseLib;
import org.luaj.vm2.lib.jse.JseMathLib;
import org.luaj.vm2.lib.jse.JseStringLib;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.IOException;
import java.io.Reader;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class ScriptManager extends SimplePreparableReloadListener<List<Map.Entry<String, Supplier<LuaTable>>>> implements IdentifiableReloadListener {
    private static final Marker MARKER = MarkerFactory.getMarker("ScriptLoader");
    private Globals globals;
    /**
     * 脚本表，key 为 {@link #getModuleName(Identifier)}。
     *
     * <p>用并发 map：它除了在 {@link #apply} 里被整体重填，还可能被
     * {@link #getScriptOrLoad(Identifier)} 在渲染线程上按需补写。</p>
     */
    private final Map<String, LuaTable> scriptMap = Maps.newConcurrentMap();
    /** 最近一次 apply 拿到的资源管理器，供按需加载使用。 */
    @Nullable
    private ResourceManager lastResourceManager;
    private final FileToIdConverter filetoidconverter;
    private final List<LuaLibrary> libraries;

    public ScriptManager(FileToIdConverter converter, List<LuaLibrary> libraries) {
        this.filetoidconverter = converter;
        this.libraries = libraries;
    }

    @Override
    @NotNull
    protected List<Map.Entry<String, Supplier<LuaTable>>> prepare(ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        // 初始化 globals
        initGlobals();
        // 打包加载函数，设置 globals 的 preload
        List<Map.Entry<String, Supplier<LuaTable>>> output = new ArrayList<>();
        for (Map.Entry<Identifier, Resource> entry : filetoidconverter.listMatchingResources(pResourceManager).entrySet()) {
            var wrappedEntry = wrapLoadingFunction(entry.getKey(), entry.getValue());
            output.add(wrappedEntry);
            globals.get("package").get("preload").set(wrappedEntry.getKey(), new LuaFunction() {
                @Override
                public LuaValue call(LuaValue modname, LuaValue env) {
                    return wrappedEntry.getValue().get();
                }
            });
        }
        return output;
    }

    @Override
    protected void apply(List<Map.Entry<String, Supplier<LuaTable>>> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        this.lastResourceManager = pResourceManager;
        scriptMap.clear();
        pObject.forEach(entry -> scriptMap.put(entry.getKey(), entry.getValue().get()));
    }

    private Map.Entry<String, Supplier<LuaTable>> wrapLoadingFunction(Identifier rawResourceLocation, Resource resource) {
        Identifier resourceLocation = filetoidconverter.fileToId(rawResourceLocation);
        String moduleName = getModuleName(resourceLocation);
        return new AbstractMap.SimpleEntry<>(moduleName, new Supplier<>() {
            private LuaTable loaded = null;

            @Override
            public LuaTable get() {
                if (loaded != null) {
                    return loaded;
                }
                try (Reader reader = resource.openAsReader()) {
                    LuaValue chunk = globals.load(reader, moduleName);
                    loaded = chunk.call().checktable(1);
                    return loaded;
                } catch (IllegalArgumentException | IOException | JsonParseException | LuaError jsonparseexception) {
                    GunMod.LOGGER.warn(MARKER, "Failed to read script file: {}", resourceLocation);
                }
                return null;
            }
        });
    }

    private void initGlobals() {
        globals = secureStandardGlobals();
        //LuaJC.install(globals);
        if (libraries != null) {
            libraries.forEach(library -> library.install(globals));
        }
    }

    private String getModuleName(Identifier resourceLocation) {
        return resourceLocation.getNamespace() + "_" + resourceLocation.getPath();
    }

    public LuaTable getScript(Identifier id) {
        return scriptMap.get(getModuleName(id));
    }

    /** 当前已加载的脚本数量（诊断用）。 */
    public int getLoadedScriptCount() {
        return scriptMap.size();
    }

    /** 当前已加载的脚本模块名快照（诊断用）。 */
    public Set<String> getLoadedModuleNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(scriptMap.keySet()));
    }

    /**
     * 取脚本：先查重载期建好的表，查不到再<b>按需</b>从资源管理器直接读盘编译。
     *
     * <h3>为什么需要按需加载</h3>
     * {@link #apply} 只会把 {@code prepare} 扫描到的脚本放进表里，而「扫描到了什么」
     * 与「内容包实际引用了什么」并不总是一致 —— 例如枪包里除主命名空间外的第二个
     * 命名空间（{@code zeta_g}）引用了 {@code lrtactical:consumable_state_machine}
     * 这类脚本时，重载期就可能拿到 {@code null}，表现为
     * {@code no corresponding state machine found for <id>}。
     *
     * <p>按需加载不看扫描结果，直接按脚本 id 去资源管理器找
     * {@code assets/<ns>/scripts/<path>.lua}，找到就编译并写入表。</p>
     *
     * @param id 脚本 id，例如 {@code lrtactical:consumable_state_machine}
     * @return 脚本表；资源确实不存在或编译失败时为 {@code null}
     */
    @Nullable
    public LuaTable getScriptOrLoad(Identifier id) {
        String moduleName = getModuleName(id);
        LuaTable cached = scriptMap.get(moduleName);
        if (cached != null) {
            return cached;
        }
        ResourceManager manager = this.lastResourceManager;
        if (manager == null) {
            return null;
        }
        Identifier file = filetoidconverter.idToFile(id);
        Resource resource = manager.getResource(file).orElse(null);
        if (resource == null) {
            return null;
        }
        LuaTable loaded = loadScriptFromResource(moduleName, file, resource);
        if (loaded != null) {
            scriptMap.put(moduleName, loaded);
        }
        return loaded;
    }

    /** 与 {@code wrapLoadingFunction} 内的加载逻辑等价，供按需加载复用。 */
    @Nullable
    private LuaTable loadScriptFromResource(String moduleName, Identifier file, Resource resource) {
        if (globals == null) {
            initGlobals();
        }
        try (Reader reader = resource.openAsReader()) {
            LuaValue chunk = globals.load(reader, moduleName);
            return chunk.call().checktable(1);
        } catch (IllegalArgumentException | IOException | JsonParseException | LuaError e) {
            GunMod.LOGGER.warn(MARKER, "Failed to read script file: {}", file, e);
        }
        return null;
    }

    private static Globals secureStandardGlobals() {
        Globals globals = new Globals();
        globals.load(new JseBaseLib());
        globals.load(new PackageLib());
        globals.load(new Bit32Lib());
        globals.load(new TableLib());
        globals.load(new JseStringLib());
        // No CoroutineLib
        globals.load(new JseMathLib());
        // No JseIoLib
        // No JseOsLib
        // No LuajavaLib
        LoadState.install(globals);
        LuaC.install(globals);
        return globals;
    }

    public static final Identifier ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "script_manager");

    @Override
    public Identifier getReloadListenerId() {
        return ID;
    }
}
