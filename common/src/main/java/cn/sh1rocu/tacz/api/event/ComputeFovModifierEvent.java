package cn.sh1rocu.tacz.api.event;

import com.tacz.guns.api.event.bus.Event;
import com.tacz.guns.api.event.bus.EventFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class ComputeFovModifierEvent extends BaseEvent {
    private final Player player;
    private final float fovModifier;
    private float newFovModifier;

    public static final Event<Callback> CALLBACK = EventFactory.createArrayBacked(Callback.class, callbacks -> event -> {
        for (Callback callback : callbacks) {
            callback.post(event);
        }
    });

    public interface Callback {
        void post(ComputeFovModifierEvent event);
    }

    public ComputeFovModifierEvent(Player player, float fovModifier) {
        this.player = player;
        this.fovModifier = fovModifier;
        this.setNewFovModifier((float) Mth.lerp(Minecraft.getInstance().options.fovEffectScale().get(), 1.0F, fovModifier));
    }

    public Player getPlayer() {
        return player;
    }

    public float getFovModifier() {
        return fovModifier;
    }

    public float getNewFovModifier() {
        return newFovModifier;
    }

    public void setNewFovModifier(float newFovModifier) {
        this.newFovModifier = newFovModifier;
    }
}
