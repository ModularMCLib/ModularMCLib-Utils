package com.modularmc.mmclutils.api.collection.map;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceFunction;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * 为一个键保存多个值，可由调用方选择底层映射和值集合。集合定义值的比较、去重与迭代顺序。
 * 使用同一实例的读写操作须在同一个线程执行，或由使用方进行同步。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public interface MultiMap<K, V> {

    /** 使用传入的实时映射，值集合在写入缺失键时由工厂创建。工厂每次须提供独立的可变集合。 */
    static <K, V> MultiMap<K, V> create(Map<K, Collection<V>> map, Supplier<Collection<V>> factory) {
        return new MultiMapWrapper<>(map, factory);
    }

    /** 创建以键的 {@link Object#equals} 区分条目的多值映射。 */
    static <K, V> MultiMap<K, V> create(Supplier<Collection<V>> factory) {
        return new MultiMapWrapper<>(new HashMap<>(), factory);
    }

    /** 创建以引用身份区分键的多值映射。各值集合自行定义值的比较与迭代顺序。 */
    static <K, V> MultiMap<K, V> createIdentity(Supplier<Collection<V>> factory) {
        return new MultiMapWrapper<>(new Reference2ReferenceOpenHashMap<>(), factory);
    }

    /** 返回底层映射的实时视图。对该映射或值集合的修改立即反映到本对象。 */
    Map<K, Collection<V>> getMap();

    /** 返回指定键的值集合。键缺失时返回不可变空列表。 */
    Collection<V> get(K key);

    /** 判断底层映射是否含该键，包括显式持有空集合的键。 */
    boolean containsKey(K key);

    /** 按该键的值集合提供的相等规则检查指定值。 */
    boolean containsValue(K key, V value);

    /** 添加一个值。可否保存重复值由集合工厂决定。 */
    void put(K key, V value);

    /** 把给定集合的元素追加到该键关联的集合。 */
    void putAll(K key, Collection<V> values);

    /** 删除指定值并返回是否有变化。删除最后一项时一并移除键。 */
    boolean remove(K key, V value);

    /** 删除此键下与给定集合匹配的所有项。结果为空时移除键。 */
    boolean removeAll(K key, Collection<V> values);

    /** 移除该键并交还原集合。键缺失时返回不可变空列表。 */
    Collection<V> remove(K key);

    /** 返回一级键的数量。 */
    int size();

    /** 遍历各值集合并求元素数的总和，包含允许重复存储的项。 */
    int valueCount();

    /** 逐项访问键和值。回调中请勿结构性修改当前映射。 */
    void forEach(BiConsumer<? super K, ? super V> action);

    /** 判断底层映射是否还有键。 */
    boolean isEmpty();

    /** 移除所有外层键以及从本映射可访问的值。 */
    void clear();

    /** 使用调用方提供的 Map 和集合工厂保存多值映射。 */
    final class MultiMapWrapper<K, V> implements MultiMap<K, V> {

        private final Map<K, Collection<V>> map;
        private final Reference2ReferenceFunction<Object, Collection<V>> factory;
        private final boolean isRefMap;

        private MultiMapWrapper(Map<K, Collection<V>> map, Supplier<Collection<V>> factory) {
            this.map = map;
            this.factory = unusedKey -> factory.get();
            this.isRefMap = map instanceof Reference2ReferenceMap;
        }

        @Override
        public Map<K, Collection<V>> getMap() {
            return map;
        }

        @Override
        public Collection<V> get(K key) {
            var values = map.get(key);
            if (values != null) return values;
            return Collections.emptyList();
        }

        @Override
        public boolean containsKey(K key) {
            return map.containsKey(key);
        }

        @Override
        public boolean containsValue(K key, V value) {
            var values = map.get(key);
            return values != null && values.contains(value);
        }

        @Override
        public void put(K key, V value) {
            if (isRefMap) {
                ((Reference2ReferenceMap<K, Collection<V>>) map).computeIfAbsent(key, factory).add(value);
            } else {
                map.computeIfAbsent(key, factory).add(value);
            }
        }

        @Override
        public void putAll(K key, Collection<V> values) {
            if (isRefMap) {
                ((Reference2ReferenceMap<K, Collection<V>>) map).computeIfAbsent(key, factory).addAll(values);
            } else {
                map.computeIfAbsent(key, factory).addAll(values);
            }
        }

        @Override
        public boolean remove(K key, V value) {
            var values = map.get(key);
            if (values != null && values.remove(value)) {
                if (values.isEmpty()) map.remove(key);
                return true;
            }
            return false;
        }

        @Override
        public boolean removeAll(K key, Collection<V> values) {
            var bound = map.get(key);
            if (bound == null || !bound.removeAll(values)) {
                return false;
            }
            if (bound.isEmpty()) {
                map.remove(key);
            }
            return true;
        }

        @Override
        public Collection<V> remove(K key) {
            var values = map.remove(key);
            if (values != null) return values;
            return Collections.emptyList();
        }

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public int valueCount() {
            int count = 0;
            for (var values : map.values()) {
                count += values.size();
            }
            return count;
        }

        @Override
        public void forEach(BiConsumer<? super K, ? super V> action) {
            map.forEach((k, vs) -> vs.forEach(v -> action.accept(k, v)));
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public void clear() {
            map.clear();
        }
    }
}
