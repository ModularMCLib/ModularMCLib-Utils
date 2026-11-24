package com.modularmc.mmclutils.api.collection.cache;

import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * 以统一接口访问相等键、身份键或自定义策略的加载缓存。实现可以强引用或弱引用值。
 * 并发查找可能同时计算同一个缺失值。供多个线程共享的计算函数应允许并发调用。
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 */
public interface LoadingCache<K, V> {

    /**
     * 查找键，缺失时计算值并尝试写入。计算依赖其他键时使用 {@link #getCacheRecursive(Object, Function)}。
     *
     * @param k              要查找的键
     * @param createFunction 缓存未命中时使用的计算函数
     * @return 本次查找或写入取得的值
     */
    V getCache(final K k, Function<? super K, ? extends V> createFunction);

    /**
     * 查找原键，缺失时将键转换为可保留的形式，再用于计算和写入。
     *
     * @param k                  用于首次查找的键
     * @param createFunction     用转换后的键计算值的函数
     * @param keyMappingFunction 转换键的函数，须保留当前缓存策略中的等价关系
     * @return 本次查找或写入取得的值
     */
    V getCache(final K k, Function<? super K, ? extends V> createFunction, UnaryOperator<K> keyMappingFunction);

    /**
     * 查找或计算缓存值。计算函数在写入操作之外运行，可在计算时访问同一缓存的其他键。
     *
     * @param k              要查找的键
     * @param createFunction 缓存未命中时使用的计算函数，调用方负责避免循环依赖
     * @return 本次查找或写入取得的值
     */
    V getCacheRecursive(final K k, Function<? super K, ? extends V> createFunction);

    /**
     * 查找原键，缺失时使用转换后的键执行可访问其他键的计算。
     *
     * @param k                  用于首次查找的键
     * @param createFunction     可访问同一缓存中其他键的计算函数
     * @param keyMappingFunction 转换键的函数，须保留当前缓存策略中的等价关系
     * @return 本次查找或写入取得的值
     */
    V getCacheRecursive(final K k, Function<? super K, ? extends V> createFunction, UnaryOperator<K> keyMappingFunction);

    /**
     * 获取构建缓存时配置的默认计算函数。
     *
     * @return 默认计算函数，构建时未配置则为空
     */
    default Function<? super K, ? extends V> createFunction() {
        return null;
    }

    /**
     * 使用默认计算函数查找或填充缓存。使用此重载前须配置默认函数。
     *
     * @param k 要查找的键
     * @return 本次查找或写入取得的值
     */
    default V getCache(final K k) {
        return getCache(k, createFunction());
    }

    /**
     * 使用已配置的默认函数查找或填充缓存，允许计算时查找其他键。
     *
     * @param k 要查找的键
     * @return 本次查找或写入取得的值
     */
    default V getCacheRecursive(final K k) {
        return getCacheRecursive(k, createFunction());
    }

    /**
     * 读取已有条目，无需配置计算函数。
     *
     * @param k 要查找的键
     * @return 已关联的值，键缺失或弱引用值已回收时为空
     */
    V getIfPresent(final K k);

    /**
     * 仅在键没有可用值时写入给定值。
     *
     * @param k 要写入的键
     * @param v 要关联的非空值
     * @return 本次操作观察到的已有值或本次写入的值
     */
    V putIfAbsent(final K k, final V v);

    /** 清除当前缓存项。与清除并行的计算仍可能写入新值。 */
    void clear();
}
