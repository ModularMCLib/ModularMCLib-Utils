package com.modularmc.mmclutils.collection;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.util.Collection;
import java.util.Map;

/** 复制为不可变集合，或直接构造键值数量较小的映射。 */
public final class CollectionCopies {

    private CollectionCopies() {}

    public static <E> ObjectList<E> immutableList(Collection<? extends E> source) {
        if (source.isEmpty()) return ObjectList.of();
        if (source instanceof ObjectImmutableList<?>) {
            // 不可变列表可以按更宽的元素类型读取。
            @SuppressWarnings("unchecked")
            ObjectList<E> immutable = (ObjectList<E>) source;
            return immutable;
        }
        return new ObjectImmutableList<>(source);
    }

    public static <E> Codec<ObjectList<E>> objectListCodec(Codec<E> elementCodec) {
        return elementCodec.listOf().xmap(
                values -> new ObjectArrayList<>(values),
                values -> values);
    }

    public static <E> ObjectSet<E> immutableSet(Collection<? extends E> source) {
        if (source.isEmpty()) return ObjectSets.emptySet();
        if (source instanceof ImmutableSetCopy<?>) {
            // 后备集合为该副本独有，可直接复用。
            @SuppressWarnings("unchecked")
            ObjectSet<E> immutable = (ObjectSet<E>) source;
            return immutable;
        }
        return new ImmutableSetCopy<>(new ObjectOpenHashSet<>(source));
    }

    public static <K, V> Object2ObjectMap<K, V> immutableMap(Map<? extends K, ? extends V> source) {
        if (source.isEmpty()) return Object2ObjectMaps.emptyMap();
        if (source instanceof ImmutableMapCopy<?, ?>) {
            // 后备映射为该副本独有，可直接复用。
            @SuppressWarnings("unchecked")
            Object2ObjectMap<K, V> immutable = (Object2ObjectMap<K, V>) source;
            return immutable;
        }
        Object2ObjectMap<K, V> copied = source.size() <= 8 ? new Object2ObjectArrayMap<>(source) :
                new Object2ObjectLinkedOpenHashMap<>(source);
        return new ImmutableMapCopy<>(copied);
    }

    public static <K, V> Object2ObjectMap<K, V> mapOf() {
        return Object2ObjectMaps.emptyMap();
    }

    public static <K, V> Object2ObjectMap<K, V> mapOf(K key, V value) {
        return Object2ObjectMaps.singleton(key, value);
    }

    public static <K, V> Object2ObjectMap<K, V> mapOf(K key1, V value1, K key2, V value2) {
        return mapOfEntries(key1, value1, key2, value2);
    }

    public static <K, V> Object2ObjectMap<K, V> mapOf(
                                                      K key1, V value1,
                                                      K key2, V value2,
                                                      K key3, V value3) {
        return mapOfEntries(key1, value1, key2, value2, key3, value3);
    }

    public static <K, V> Object2ObjectMap<K, V> mapOf(
                                                      K key1, V value1,
                                                      K key2, V value2,
                                                      K key3, V value3,
                                                      K key4, V value4) {
        return mapOfEntries(key1, value1, key2, value2, key3, value3, key4, value4);
    }

    /** 从四对以上的交替键值参数创建不可变映射。 */
    public static <K, V> Object2ObjectMap<K, V> mapOf(Object... entries) {
        return mapOfEntries(entries);
    }

    private static <K, V> Object2ObjectMap<K, V> mapOfEntries(Object... entries) {
        if ((entries.length & 1) != 0) {
            throw new IllegalArgumentException("Collection map entries must be key/value pairs");
        }
        Object2ObjectLinkedOpenHashMap<K, V> result = new Object2ObjectLinkedOpenHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            @SuppressWarnings("unchecked")
            K key = (K) entries[index];
            @SuppressWarnings("unchecked")
            V value = (V) entries[index + 1];
            if (result.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate collection map key: " + key);
            }
            result.put(key, value);
        }
        return new ImmutableMapCopy<>(result);
    }

    private static final class ImmutableMapCopy<K, V> extends Object2ObjectMaps.UnmodifiableMap<K, V> {

        private ImmutableMapCopy(Object2ObjectMap<K, V> owned) {
            super(owned);
        }
    }

    private static final class ImmutableSetCopy<E> extends ObjectSets.UnmodifiableSet<E> {

        private ImmutableSetCopy(ObjectSet<E> owned) {
            super(owned);
        }
    }
}
