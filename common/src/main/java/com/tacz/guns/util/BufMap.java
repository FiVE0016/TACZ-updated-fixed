package com.tacz.guns.util;

import net.minecraft.network.FriendlyByteBuf;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 26.3 removed {@code FriendlyByteBuf#readMap} and {@code #writeMap}, and left no replacement
 * behind: {@code ByteBufCodecs} grew {@code collection()} but never a {@code map()}. These two
 * keep vanilla's old wire shape — a varint entry count, then key/value pairs — so the payloads
 * TACZ sends are unchanged from the 26.2 build.
 */
public final class BufMap {
    private BufMap() {
    }

    public static <K, V> Map<K, V> read(FriendlyByteBuf buf, Function<FriendlyByteBuf, K> keyReader, Function<FriendlyByteBuf, V> valueReader) {
        int size = buf.readVarInt();
        Map<K, V> map = new HashMap<>(Math.max(4, size));
        for (int i = 0; i < size; i++) {
            K key = keyReader.apply(buf);
            map.put(key, valueReader.apply(buf));
        }
        return map;
    }

    public static <K, V> void write(FriendlyByteBuf buf, Map<K, V> map, BiConsumer<FriendlyByteBuf, K> keyWriter, BiConsumer<FriendlyByteBuf, V> valueWriter) {
        buf.writeVarInt(map.size());
        map.forEach((key, value) -> {
            keyWriter.accept(buf, key);
            valueWriter.accept(buf, value);
        });
    }
}
