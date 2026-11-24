package com.modularmc.mmclutils.api.collection.cache;

import java.util.function.Function;
import java.util.function.UnaryOperator;

/** 按键获取或计算缓存值。提供给计算函数的执行时机由各方法说明，缺失返回空值，缓存支持主动清除。 */
public interface LoadingCache<K, V> {

    V getCache(final K k, Function<? super K, ? extends V> createFunction);

    V getCache(final K k, Function<? super K, ? extends V> createFunction, UnaryOperator<K> keyMappingFunction);

    V getCacheRecursive(final K k, Function<? super K, ? extends V> createFunction);

    V getCacheRecursive(final K k, Function<? super K, ? extends V> createFunction, UnaryOperator<K> keyMappingFunction);

    /** 根据参数构造容器。 */
    default Function<? super K, ? extends V> createFunction() {
        return null;
    }

    default V getCache(final K k) {
        return getCache(k, createFunction());
    }

    default V getCacheRecursive(final K k) {
        return getCacheRecursive(k, createFunction());
    }

    V getIfPresent(final K k);

    V putIfAbsent(final K k, final V v);

    void clear();
}
