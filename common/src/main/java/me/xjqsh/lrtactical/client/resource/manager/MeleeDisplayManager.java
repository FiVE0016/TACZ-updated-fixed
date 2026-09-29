package me.xjqsh.lrtactical.client.resource.manager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.manager.JsonDataManager;
import me.xjqsh.lrtactical.client.resource.display.MeleeDisplayInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/**
 * 加载 {@code assets/<ns>/display/melee/*.json}。
 *
 * <p>Builds on TACZ's {@link JsonDataManager}, an {@code IdentifiableReloadListener} that every loader accepts
 * as a client reload listener.
 *
 * <h2>为什么不用 {@code DisplayManager} 而是直接继承 {@code JsonDataManager}</h2>
 * TACZ 的 {@code DisplayManager<T extends IDisplay>} 要求数据类实现 {@code IDisplay}
 * 并有无参可反序列化的形态；而 LRTactical 的 display 是「先反序列化成 POJO record，
 * 再经 {@code create()} 校验并解析出模型/动画/状态机」的两段式，
 * 二者形状不同。上游也是直接继承 {@code JsonDataManager} 并覆写 {@code apply}，此处照搬。
 *
 * <p>{@code dataClass} 传 {@code null} 是刻意的：本类不走基类的
 * {@code parseJson(element) -> gson.fromJson(element, dataClass)} 路径，
 * 而是在 {@code apply} 里显式指定 POJO 类型。
 *
 * <h2>Must apply after TACZ's model, animation and script listeners</h2>
 * {@code MeleeDisplayInstance#create} fetches geo models, bedrock animations and Lua scripts from
 * {@code ClientAssetsManager} synchronously; applied before those listeners it gets {@code null} for all of them
 * ("no corresponding model found"). Reload listeners apply in the order they are added, so
 * {@code ClientSetupEvent#registerClientReloadListeners} hands this one out after TACZ's.
 *
 * <h2>单个文件解析失败不影响其它文件</h2>
 * {@code create()} 用 {@code Preconditions} 抛 {@code IllegalArgumentException}
 * 来表达「这个 display 缺字段/引用了不存在的模型」。这里逐个 catch 并记日志 ——
 * 一个内容包写错一把刀，不该让整个资源重载失败。
 */
public class MeleeDisplayManager extends JsonDataManager<MeleeDisplayInstance> {
    public MeleeDisplayManager(Gson pGson) {
        super(null, pGson, "display/melee", "LrMeleeDisplay");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        dataMap.clear();
        for (Map.Entry<Identifier, JsonElement> entry : pObject.entrySet()) {
            Identifier id = entry.getKey();
            try {
                var pojo = getGson().fromJson(entry.getValue(), MeleeDisplayInstance.MeleeDisplay.class);
                dataMap.put(id, MeleeDisplayInstance.create(pojo, id));
            } catch (JsonParseException | IllegalArgumentException e) {
                GunMod.LOGGER.error(getMarker(), "Failed to load display file {}", id, e);
            }
        }
    }
}
