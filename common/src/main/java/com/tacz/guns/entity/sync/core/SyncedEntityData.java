package com.tacz.guns.entity.sync.core;
import com.tacz.guns.platform.Platform;

import com.google.common.collect.ImmutableSet;
import com.tacz.guns.GunMod;
import com.tacz.guns.init.CommonRegistry;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Author: MrCrayfish.
 * Open source at <a href="https://github.com/MrCrayfish/Framework">Github</a> under LGPL License.
 */
public class SyncedEntityData {
    private static final Marker SYNCED_ENTITY_DATA_MARKER = MarkerFactory.getMarker("SYNCED_ENTITY_DATA_TAC_COPY");
    /**
     * 常量式单例。<b>不要改回懒初始化</b>：启动期客户端主线程、集成服务端线程与
     * 网络解码线程都可能并发首次调用 {@code instance()}，无 volatile/synchronized
     * 的懒初始化会造出两个实例——同步键注册进 A，而键映射包从 B（空表）构造。
     * 客户端收到空映射后会清掉本来正确的网络 id 表，随后任何
     * {@code s2c_update_entity_data} 都会以 "Synced key does not exist for id"
     * 解码失败并踢出玩家（实机 1.26.3 撞到过）。
     */
    private static final SyncedEntityData INSTANCE = new SyncedEntityData();

    private final Set<SyncedClassKey<?>> registeredClassKeys = new HashSet<>();
    private final Object2ObjectMap<Identifier, SyncedClassKey<?>> idToClassKey = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, SyncedClassKey<?>> classNameToClassKey = new Object2ObjectOpenHashMap<>();
    private final Map<String, Boolean> clientClassNameCapabilityCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> serverClassNameCapabilityCache = new ConcurrentHashMap<>();

    private final Set<SyncedDataKey<?, ?>> registeredDataKeys = new HashSet<>();
    private final Reference2ObjectMap<SyncedClassKey<?>, HashMap<Identifier, SyncedDataKey<?, ?>>> classToKeys = new Reference2ObjectOpenHashMap<>();
    private final Reference2IntMap<SyncedDataKey<?, ?>> internalIds = new Reference2IntOpenHashMap<>();
    /** 非 final：{@link #updateMappings} 用整表替换来避免清表窗口（见该方法注释）。 */
    private Int2ReferenceMap<SyncedDataKey<?, ?>> syncedIdToKey = new Int2ReferenceOpenHashMap<>();

    private final AtomicInteger nextIdTracker = new AtomicInteger();
    private final List<Entity> dirtyEntities = new ArrayList<>();
    private boolean dirty = false;

    private SyncedEntityData() {
    }

    public static SyncedEntityData instance() {
        return INSTANCE;
    }

    private <E extends Entity> void registerClassKey(SyncedClassKey<E> classKey) {
        if (!this.registeredClassKeys.contains(classKey)) {
            this.registeredClassKeys.add(classKey);
            this.idToClassKey.put(classKey.id(), classKey);
            this.classNameToClassKey.put(classKey.entityClass().getName(), classKey);
        }
    }

    /**
     * Registers a synced data key into the system.
     *
     * @param dataKey a synced data key instance
     */
    public synchronized <E extends Entity, T> void registerDataKey(SyncedDataKey<E, T> dataKey) {
        Identifier keyId = dataKey.id();
        SyncedClassKey<E> classKey = dataKey.classKey();
        if (CommonRegistry.isLoadComplete()) {
            throw new IllegalStateException(String.format("Tried to register synced data key %s for %s after game initialization", keyId, classKey.id()));
        }
        if (this.registeredDataKeys.contains(dataKey)) {
            throw new IllegalArgumentException(String.format("The synced data key %s for %s is already registered", keyId, classKey.id()));
        }
        // Attempt to register the class key. Will ignore if already registered.
        this.registerClassKey(dataKey.classKey());
        this.registeredDataKeys.add(dataKey);
        this.classToKeys.computeIfAbsent(classKey, c -> new HashMap<>()).put(keyId, dataKey);
        int nextId = this.nextIdTracker.getAndIncrement();
        this.internalIds.put(dataKey, nextId);
        this.syncedIdToKey.put(nextId, dataKey);
        GunMod.LOGGER.info(SYNCED_ENTITY_DATA_MARKER, "Registered synced data key {} for {}", dataKey.id(), classKey.id());
    }

    /**
     * Sets the value of a synced data key to the specified player
     *
     * @param entity the player to assign the value to
     * @param key    a registered synced data key
     * @param value  a new value that matches the synced data key type
     */
    public <E extends Entity, T> void set(E entity, SyncedDataKey<?, ?> key, T value) {
        if (!this.registeredDataKeys.contains(key)) {
            String keys = this.registeredDataKeys.stream().map(k -> k.pairKey().toString()).collect(Collectors.joining(",", "[", "]"));
            GunMod.LOGGER.info(SYNCED_ENTITY_DATA_MARKER, "Registered keys before throwing exception: {}", keys);
            throw new IllegalArgumentException(String.format("The synced data key %s for %s is not registered!", key.id(), key.classKey().id()));
        }
        DataHolder holder = this.getDataHolder(entity);
        if (holder != null && holder.set(entity, key, value)) {
            if (!entity.level().isClientSide()) {
                this.dirty = true;
                this.dirtyEntities.add(entity);
            }
        }
    }

    /**
     * Gets the value for the synced data key from the specified player. It is best to check that
     * the player is alive before getting the value.
     *
     * @param entity the player to retrieve the data from
     * @param key    a registered synced data key
     */
    public <E extends Entity, T> T get(E entity, SyncedDataKey<E, T> key) {
        if (!this.registeredDataKeys.contains(key)) {
            String keys = this.registeredDataKeys.stream().map(k -> k.pairKey().toString()).collect(Collectors.joining(",", "[", "]"));
            GunMod.LOGGER.info(SYNCED_ENTITY_DATA_MARKER, "Registered keys before throwing exception: {}", keys);
            throw new IllegalArgumentException(String.format("The synced data key %s for %s is not registered!", key.id(), key.classKey().id()));
        }
        DataHolder holder = this.getDataHolder(entity);
        return holder != null ? holder.get(key) : key.defaultValueSupplier().get();
    }

    public int getInternalId(SyncedDataKey<?, ?> key) {
        if (!this.internalIds.containsKey(key)) {
            // 未注册的键在这里原本静默返回 0，会把网络流写歪成"另一个键的值"。
            GunMod.LOGGER.error(SYNCED_ENTITY_DATA_MARKER,
                    "Synced data key {} for {} was never registered - refusing to write a bogus network id",
                    key.id(), key.classKey().id());
        }
        return this.internalIds.getInt(key);
    }

    @Nullable
    public SyncedClassKey<?> getClassKey(Identifier id) {
        return idToClassKey.get(id);
    }

    @Nullable
    public SyncedDataKey<?, ?> getKey(int id) {
        return this.syncedIdToKey.get(id);
    }

    @Nullable
    public SyncedDataKey<?, ?> getKey(SyncedClassKey<?> classKey, Identifier dataKey) {
        Map<Identifier, SyncedDataKey<?, ?>> keys = SyncedEntityData.instance().classToKeys.get(classKey);
        if (keys == null) {
            return null;
        }
        return keys.get(dataKey);
    }

    public Set<SyncedDataKey<?, ?>> getKeys() {
        return ImmutableSet.copyOf(this.registeredDataKeys);
    }

    @Nullable
    public DataHolder getDataHolder(Entity entity) {
        if (!this.hasSyncedDataKey(entity.getClass())) {
            return null;
        }
        // Lazy creation closes the old lifecycle hole where every caller used maybeGet(), so no
        // provider ever existed and all set() calls silently discarded their values.
        return DataHolderCapabilityProvider.get(entity).getDataHolder().orElse(null);
    }

//    public boolean hasSyncedDataKey(Class<? extends Entity> entityClass) {
//        // Gets the class name capability cache for the effective side.
//        // This is needed to avoid concurrency issue due to client and server threads;
//        // fast util does not support concurrent maps.
//        Object2BooleanMap<String> cache = EffectiveSide.get().isClient() ? this.clientClassNameCapabilityCache : this.serverClassNameCapabilityCache;
//        // It's possible that the entity doesn't have a key, but it's superclass or subsequent does have a synced data key.
//        // In order to prevent checking this every time we attach the capability, a simple one time check can be performed then cache the result.
//        return cache.computeIfAbsent(entityClass.getName(), c -> {
//            Class<?> targetClass = entityClass;
//            // Should be good enough
//            while (!targetClass.isAssignableFrom(Entity.class)) {
//                if (this.classNameToClassKey.containsKey(targetClass.getName())) {
//                    return true;
//                }
//                targetClass = targetClass.getSuperclass();
//            }
//            return false;
//        });
//    }

    public boolean hasSyncedDataKey(Class<? extends Entity> entityClass) {
        /* It's possible that the entity doesn't have a key, but it's superclass or subsequent does
         * have a synced data key. In order to prevent checking this every time we attach the
         * capability, a simple one time check can be performed then cache the result. */
        return this.getClassNameCapabilityCache(Platform.INSTANCE.isPhysicalClient())
                .computeIfAbsent(entityClass.getName(), c ->
                {
                    Class<?> targetClass = entityClass;
                    while (!targetClass.isAssignableFrom(Entity.class)) // Should be good enough
                    {
                        if (this.classNameToClassKey.containsKey(targetClass.getName())) {
                            return true;
                        }
                        targetClass = targetClass.getSuperclass();
                    }
                    return false;
                });
    }

    private Map<String, Boolean> getClassNameCapabilityCache(boolean client) {
        return client ? this.clientClassNameCapabilityCache : this.serverClassNameCapabilityCache;
    }

    public boolean updateMappings(Map<Identifier, List<Pair<Identifier, Integer>>> keyMap) {
        // 先建新表、最后整表替换：旧实现是 clear() 之后逐条 put，网络解码线程若在
        // 这个窗口里解包，会读到空表/半表并把本来有效的 id 判为未知，直接以
        // DecoderException 踢人（单人集成服里两个线程共享同一实例，窗口真实存在）。
        Int2ReferenceMap<SyncedDataKey<?, ?>> rebuilt = new Int2ReferenceOpenHashMap<>();

        List<Pair<Identifier, Identifier>> missingKeys = new ArrayList<>();
        keyMap.forEach((classId, list) -> {
            SyncedClassKey<?> classKey = this.idToClassKey.get(classId);
            if (classKey == null || !this.classToKeys.containsKey(classKey)) {
                list.forEach(pair -> missingKeys.add(Pair.of(classId, pair.getLeft())));
                return;
            }

            Map<Identifier, SyncedDataKey<?, ?>> keys = this.classToKeys.get(classKey);
            list.forEach(pair -> {
                SyncedDataKey<?, ?> syncedDataKey = keys.get(pair.getLeft());
                if (syncedDataKey == null) {
                    missingKeys.add(Pair.of(classId, pair.getLeft()));
                    return;
                }
                rebuilt.put((int) pair.getRight(), syncedDataKey);
            });
        });

        this.syncedIdToKey = rebuilt;

        if (!missingKeys.isEmpty()) {
            String keys = missingKeys.stream().map(Object::toString).collect(Collectors.joining(",", "[", "]"));
            GunMod.LOGGER.info(SYNCED_ENTITY_DATA_MARKER, "Received unknown synced keys: {}", keys);
        }

        return missingKeys.isEmpty();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public List<Entity> getDirtyEntities() {
        return dirtyEntities;
    }
}
