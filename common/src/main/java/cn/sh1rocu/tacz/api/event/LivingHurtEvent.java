package cn.sh1rocu.tacz.api.event;

import com.tacz.guns.api.event.bus.Event;
import com.tacz.guns.api.event.bus.EventFactory;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public class LivingHurtEvent extends LivingEvent implements ICancellableEvent {
    private final DamageSource source;
    private float amount;
    public static final Event<Callback> CALLBACK = EventFactory.createWithPhases(Callback.class, callbacks -> event -> {
        for (Callback e : callbacks)
            e.onLivingHurt(event);
    }, Event.HIGHEST, Event.HIGH, Event.DEFAULT_PHASE, Event.LOW, Event.LOWEST);

    public LivingHurtEvent(LivingEntity entity, DamageSource source, float amount) {
        super(entity);
        this.source = source;
        this.amount = amount;
    }

    public DamageSource getSource() {
        return source;
    }

    public float getAmount() {
        return amount;
    }

    public void setAmount(float amount) {
        this.amount = amount;
    }

    public interface Callback {
        void onLivingHurt(LivingHurtEvent event);
    }
}