package com.modularmc.mmclutils.collection;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;

import java.util.Iterator;
import java.util.Map;

/** fastutil 条目的快速迭代入口。调用方需要立即读取可能复用的条目。 */
public final class MapIterators {

    private MapIterators() {}

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
}
