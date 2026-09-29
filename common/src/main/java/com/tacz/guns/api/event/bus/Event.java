package com.tacz.guns.api.event.bus;

import com.tacz.guns.GunMod;
import net.minecraft.resources.Identifier;

/**
 * A loader-independent callback event, API-compatible with the subset of Fabric's
 * {@code Event} that TACZ uses: listeners register (optionally in a phase) and callers
 * fire the event through {@link #invoker()}.
 * <p>
 * Phases run in the order {@link #HIGHEST}, {@link #HIGH}, {@link #DEFAULT_PHASE},
 * {@link #LOW}, {@link #LOWEST}; any other phase runs after them in registration order
 * unless positioned with {@link #addPhaseOrdering}.
 */
public abstract class Event<T> {
    public static final Identifier HIGHEST = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "event_highest_priority");
    public static final Identifier HIGH = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "event_high_priority");
    public static final Identifier DEFAULT_PHASE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "default");
    public static final Identifier LOW = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "event_low_priority");
    public static final Identifier LOWEST = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "event_lowest_priority");

    protected volatile T invoker;

    /**
     * @return a callback that dispatches to every registered listener
     */
    public final T invoker() {
        return invoker;
    }

    public void register(T listener) {
        register(DEFAULT_PHASE, listener);
    }

    public abstract void register(Identifier phase, T listener);

    public abstract void addPhaseOrdering(Identifier firstPhase, Identifier secondPhase);
}
