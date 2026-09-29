package cn.sh1rocu.tacz.api.event;

import com.tacz.guns.api.event.bus.Event;
import com.tacz.guns.api.event.bus.EventFactory;
import net.minecraft.world.entity.Entity;

public class EntityRemoveEvent extends BaseEvent {
    public static final Event<Callback> EVENT = EventFactory.createArrayBacked(Callback.class, callbacks -> event -> {
        for (Callback e : callbacks) e.onEntityRemove(event);
    });

    private final Entity entity;

    public EntityRemoveEvent(Entity entity) {
        this.entity = entity;
    }

    public Entity getEntity() {
        return entity;
    }

    public interface Callback {
        void onEntityRemove(EntityRemoveEvent event);
    }
}