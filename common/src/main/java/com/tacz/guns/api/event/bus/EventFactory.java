package com.tacz.guns.api.event.bus;

import net.minecraft.resources.Identifier;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Creates {@link Event}s. Mirrors the Fabric {@code EventFactory} methods TACZ calls, so event
 * declarations stay unchanged across loaders.
 */
public final class EventFactory {
    private EventFactory() {
    }

    public static <T> Event<T> createArrayBacked(Class<? super T> type, Function<T[], T> invokerFactory) {
        return new ArrayBackedEvent<>(type, null, invokerFactory);
    }

    public static <T> Event<T> createArrayBacked(Class<T> type, T emptyInvoker, Function<T[], T> invokerFactory) {
        return new ArrayBackedEvent<>(type, emptyInvoker, invokerFactory);
    }

    public static <T> Event<T> createWithPhases(Class<? super T> type, Function<T[], T> invokerFactory, Identifier... defaultPhases) {
        ArrayBackedEvent<T> event = new ArrayBackedEvent<>(type, null, invokerFactory);
        for (int i = 1; i < defaultPhases.length; i++) {
            event.addPhaseOrdering(defaultPhases[i - 1], defaultPhases[i]);
        }
        return event;
    }

    private static final class ArrayBackedEvent<T> extends Event<T> {
        private final Class<? super T> type;
        private final T emptyInvoker;
        private final Function<T[], T> invokerFactory;
        private final List<Identifier> phaseOrder = new ArrayList<>(List.of(HIGHEST, HIGH, DEFAULT_PHASE, LOW, LOWEST));
        private final Map<Identifier, List<T>> listeners = new LinkedHashMap<>();
        private final Object lock = new Object();

        private ArrayBackedEvent(Class<? super T> type, T emptyInvoker, Function<T[], T> invokerFactory) {
            this.type = type;
            this.emptyInvoker = emptyInvoker;
            this.invokerFactory = invokerFactory;
            rebuildInvoker();
        }

        @Override
        public void register(Identifier phase, T listener) {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(listener, "listener");
            synchronized (lock) {
                if (!phaseOrder.contains(phase)) {
                    phaseOrder.add(phase);
                }
                listeners.computeIfAbsent(phase, id -> new ArrayList<>()).add(listener);
                rebuildInvoker();
            }
        }

        @Override
        public void addPhaseOrdering(Identifier firstPhase, Identifier secondPhase) {
            Objects.requireNonNull(firstPhase, "firstPhase");
            Objects.requireNonNull(secondPhase, "secondPhase");
            if (firstPhase.equals(secondPhase)) {
                throw new IllegalArgumentException("Tried to order a phase against itself: " + firstPhase);
            }
            synchronized (lock) {
                if (!phaseOrder.contains(secondPhase)) {
                    phaseOrder.add(secondPhase);
                }
                phaseOrder.remove(firstPhase);
                phaseOrder.add(phaseOrder.indexOf(secondPhase), firstPhase);
                rebuildInvoker();
            }
        }

        @SuppressWarnings("unchecked")
        private void rebuildInvoker() {
            List<T> ordered = new ArrayList<>();
            for (Identifier phase : phaseOrder) {
                List<T> phaseListeners = listeners.get(phase);
                if (phaseListeners != null) {
                    ordered.addAll(phaseListeners);
                }
            }
            if (ordered.isEmpty() && emptyInvoker != null) {
                invoker = emptyInvoker;
                return;
            }
            T[] array = (T[]) Array.newInstance(type, ordered.size());
            invoker = invokerFactory.apply(ordered.toArray(array));
        }
    }
}
