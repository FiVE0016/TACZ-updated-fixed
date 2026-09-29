package com.tacz.guns.platform;

import java.util.ServiceLoader;

/**
 * Loads the single loader-specific implementation of a platform service.
 * <p>
 * Each loader project registers its implementations in {@code META-INF/services}; common code
 * only ever talks to the interfaces in {@code com.tacz.guns.platform}.
 */
public final class Services {
    private Services() {
    }

    public static <T> T load(Class<T> type) {
        return ServiceLoader.load(type, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No implementation of " + type.getName() + " is registered for this mod loader"));
    }
}
