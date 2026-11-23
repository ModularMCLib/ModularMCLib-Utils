package com.modularmc.mmclutils.serialization;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 用原版 Codec 读写集合，并在解码时选用适合用途的容器。 */
public final class CollectionCodecs {

    private CollectionCodecs() {}

    public static <E> Codec<List<E>> mutableList(Codec<E> elements) {
        return elements.listOf().xmap(ObjectArrayList::new, List::copyOf);
    }

    public static <E> Codec<Set<E>> set(Codec<E> elements) {
        return elements.listOf().xmap(ObjectLinkedOpenHashSet::new, List::copyOf);
    }

    public static <E extends Enum<E>> Codec<Set<E>> enumSet(Class<E> type, Codec<E> elements) {
        return elements.listOf().xmap(values -> {
            Set<E> result = EnumSet.noneOf(type);
            result.addAll(values);
            return result;
        }, List::copyOf);
    }

    public static <K, V> Codec<Map<K, V>> orderedMap(Codec<K> keys, Codec<V> values) {
        return Codec.unboundedMap(keys, values).xmap(Object2ObjectLinkedOpenHashMap::new, map -> map);
    }

    public static <K extends Enum<K>, V> Codec<Map<K, V>> enumMap(Class<K> type, Codec<K> keys, Codec<V> values) {
        return Codec.unboundedMap(keys, values).xmap(encoded -> {
            Map<K, V> result = new EnumMap<>(type);
            result.putAll(encoded);
            return result;
        }, map -> map);
    }
}
