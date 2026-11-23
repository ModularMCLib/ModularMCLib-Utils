package com.modularmc.mmclutils.collection.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** 按键保存可复用的值。 计算函数在锁外运行，并发写入在分段锁内合并。 */
public final class HashCache<K, V> implements LoadingCache<K, V> {

    private final ConcurrentHashMap<K, V> map;
    private final Function<? super K, ? extends V> createFunction;

    public HashCache() {
        this(null);
    }

    /** 使用默认并发度。创建函数由参数决定，可省略。 */
    public HashCache(Function<? super K, ? extends V> createFunction) {
        this.map = new ConcurrentHashMap<>();
        this.createFunction = createFunction;
    }

    @Override
    public Function<? super K, ? extends V> createFunction() {
        return createFunction;
    }

    @Override
    public V getCache(final K k, Function<? super K, ? extends V> createFunction) {
        return map.computeIfAbsent(k, createFunction);
    }

    @Override
    public V getCache(final K k, Function<? super K, ? extends V> createFunction, UnaryOperator<K> keyMappingFunction) {
        V v = map.get(k);
        if (v != null) {
            return v;
        }
        K mapped = keyMappingFunction.apply(k);
        v = createFunction.apply(mapped);
        V prev = map.putIfAbsent(mapped, v);
        return prev == null ? v : prev;
    }

    @Override
    public V getCacheRecursive(final K k, Function<? super K, ? extends V> createFunction) {
        V v = map.get(k);
        if (v != null) {
            return v;
        }

        v = createFunction.apply(k);
        V prev = map.putIfAbsent(k, v);
        return prev == null ? v : prev;
    }

    @Override
    public V getCacheRecursive(final K k, Function<? super K, ? extends V> createFunction, UnaryOperator<K> keyMappingFunction) {
        V v = map.get(k);
        if (v != null) {
            return v;
        }
        K mapped = keyMappingFunction.apply(k);
        v = createFunction.apply(mapped);
        V prev = map.putIfAbsent(mapped, v);
        return prev == null ? v : prev;
    }

    @Override
    public V getIfPresent(final K k) {
        return map.get(k);
    }

    @Override
    public V putIfAbsent(final K k, final V v) {
        V prev = map.putIfAbsent(k, v);
        return prev == null ? v : prev;
    }

    @Override
    public void clear() {
        map.clear();
    }
}
