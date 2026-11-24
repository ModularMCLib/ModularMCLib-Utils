package com.modularmc.mmclutils.utils;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;

import java.util.Iterator;
import java.util.Map;

/** 构造不可变映射与快速迭代 fastutil 条目。快速迭代器可能复用条目，调用方须立即读取。 */
public final class MapUtils {

    private MapUtils() {}

    public static <K, V> ObjectIterator<Object2ObjectMap.Entry<K, V>> fast(Object2ObjectMap<K, V> map) {
        return Object2ObjectMaps.fastIterator(map);
    }

    public static <K, V> ObjectIterator<Reference2ObjectMap.Entry<K, V>> fast(Reference2ObjectMap<K, V> map) {
        return Reference2ObjectMaps.fastIterator(map);
    }

    /** 兼容标准 Map，识别 fastutil 映射并选择相应的快速迭代器。 */
    @SuppressWarnings("unchecked")
    public static <K, V> Iterator<Map.Entry<K, V>> fastIterator(Map<K, V> map) {
        if (map instanceof Object2ObjectMap<?, ?> objectMap) {
            return (Iterator<Map.Entry<K, V>>) (Iterator<?>) fast(objectMap);
        }
        if (map instanceof Reference2ObjectMap<?, ?> referenceMap) {
            return (Iterator<Map.Entry<K, V>>) (Iterator<?>) fast(referenceMap);
        }
        return map.entrySet().iterator();
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
            throw new IllegalArgumentException("映射参数必须成对提供键和值");
        }
        Object2ObjectLinkedOpenHashMap<K, V> result = new Object2ObjectLinkedOpenHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            @SuppressWarnings("unchecked")
            K key = (K) entries[index];
            @SuppressWarnings("unchecked")
            V value = (V) entries[index + 1];
            if (result.containsKey(key)) {
                throw new IllegalArgumentException("映射含有重复的键 " + key);
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
}
