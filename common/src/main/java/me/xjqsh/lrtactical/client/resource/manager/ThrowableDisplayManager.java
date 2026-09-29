package me.xjqsh.lrtactical.client.resource.manager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.manager.JsonDataManager;
import me.xjqsh.lrtactical.client.resource.display.ThrowableDisplayInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/**
 * 加载 {@code assets/<ns>/display/throwable/*.json}。
 *
 * <p>设计与 {@link MeleeDisplayManager} 完全一致（包括必须排在 TACZ 的模型/动画/脚本 listener 之后），
 * 说明见该类注释。
 */
public class ThrowableDisplayManager extends JsonDataManager<ThrowableDisplayInstance> {
    public ThrowableDisplayManager(Gson pGson) {
        super(null, pGson, "display/throwable", "LrThrowableDisplay");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        dataMap.clear();
        for (Map.Entry<Identifier, JsonElement> entry : pObject.entrySet()) {
            Identifier id = entry.getKey();
            try {
                var pojo = getGson().fromJson(entry.getValue(), ThrowableDisplayInstance.ThrowableDisplay.class);
                dataMap.put(id, ThrowableDisplayInstance.create(pojo, id));
            } catch (JsonParseException | IllegalArgumentException e) {
                GunMod.LOGGER.error(getMarker(), "Failed to load display file {}", id, e);
            }
        }
    }
}
