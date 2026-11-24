package com.modularmc.mmclutils.api.collection.map;

import com.modularmc.mmclutils.api.function.TriConsumer;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceFunction;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 按外层键和内层键索引单个值。写入时按需创建内层映射，两层键分别由各自的映射定义比较规则。
 * 使用同一实例的读写操作须在同一个线程执行，或由使用方进行同步。
 *
 * @param <K1> 外层键类型
 * @param <K2> 内层键类型
 * @param <V>  值类型
 */
public interface NestedMap<K1, K2, V> {

    /** 使用传入的外层 Map。工厂每次须返回一个独立且比较规则一致的内层 Map。 */
    static <K1, K2, V> NestedMap<K1, K2, V> create(Map<K1, Map<K2, V>> map, Supplier<Map<K2, V>> factory) {
        return new NestedMapWrapper<>(map, factory);
    }

    /** 由 {@link Object#equals} 比较外层键，内层键按提供的 Map 工厂定义。 */
    static <K1, K2, V> NestedMap<K1, K2, V> create(Supplier<Map<K2, V>> factory) {
        return new NestedMapWrapper<>(new HashMap<>(), factory);
    }

    /** 按引用身份比较外层键，内层键按提供的 Map 工厂定义。 */
    static <K1, K2, V> NestedMap<K1, K2, V> createIdentity(Supplier<Map<K2, V>> factory) {
        return new NestedMapWrapper<>(new Reference2ReferenceOpenHashMap<>(), factory);
    }

    /** 返回可直接修改的底层映射，各内层映射同样可见。 */
    Map<K1, Map<K2, V>> getMap();

    /** 按两级键查一个值。外层键不存在时返回空值，内层键缺失时沿用内层映射的返回值。 */
    V get(K1 k1, K2 k2);

    /** 返回内层映射的实时视图。外层键缺失时返回不可变空映射。 */
    Map<K2, V> get(K1 k1);

    /** 判断是否存在该外层键。 */
    boolean containsKey(K1 k1);

    /** 判断两级路径下是否存在该内层键。 */
    boolean containsKey(K1 k1, K2 k2);

    /** 存入值，并返回内层映射的原值或默认返回值。首次写入时分配内层映射。 */
    V put(K1 k1, K2 k2, V value);

    /** 在内层键缺失时用该键计算并记录结果。返回值沿用内层映射的计算规则。 */
    V computeIfAbsent(K1 k1, K2 k2, Reference2ReferenceFunction<? super K2, ? extends V> mappingFunction);

    /** 移除内层键并返回原值。外层键不存在时返回空值。移除非空原值并清空内层时顺带删除外层键。 */
    V remove(K1 k1, K2 k2);

    /** 移除外层键并交还对应映射。键缺失时返回不可变空映射。 */
    Map<K2, V> remove(K1 k1);

    /** 返回外层键的数量。 */
    int size();

    /** 遍历内层映射，统计已存储的两级键值对总数。 */
    int valueCount();

    /** 遍历每个两级键值对。回调中请勿结构性修改映射。 */
    void forEach(TriConsumer<? super K1, ? super K2, ? super V> action);

    /** 判断外层映射是否还有键。 */
    boolean isEmpty();

    /** 移除所有外层键以及从本映射可访问的值。 */
    void clear();

    /** 使用调用方选择的内外两层 Map 保存两级键值对。 */
    final class NestedMapWrapper<K1, K2, V> implements NestedMap<K1, K2, V> {

        private final Map<K1, Map<K2, V>> map;
        private final Reference2ReferenceFunction<Object, Map<K2, V>> factory;
        private final boolean isRefMap;

        private boolean isInnerRefMap;

        private NestedMapWrapper(Map<K1, Map<K2, V>> map, Supplier<Map<K2, V>> factory) {
            this.map = map;
            this.factory = unusedKey -> {
                var innerMap = factory.get();
                if (innerMap instanceof Reference2ReferenceMap) {
                    isInnerRefMap = true;
                }
                return innerMap;
            };
            this.isRefMap = map instanceof Reference2ReferenceMap;
        }

        @Override
        public Map<K1, Map<K2, V>> getMap() {
            return map;
        }

        @Override
        public Map<K2, V> get(K1 k1) {
            var innerMap = map.get(k1);
            if (innerMap != null) return innerMap;
            return Collections.emptyMap();
        }

        @Override
        public boolean containsKey(K1 k1) {
            return map.containsKey(k1);
        }

        @Override
        public boolean containsKey(K1 k1, K2 k2) {
            var innerMap = map.get(k1);
            return innerMap != null && innerMap.containsKey(k2);
        }

        @Override
        public V get(K1 k1, K2 k2) {
            var innerMap = map.get(k1);
            return innerMap == null ? null : innerMap.get(k2);
        }

        @Override
        public V put(K1 k1, K2 k2, V value) {
            return innerMapFor(k1).put(k2, value);
        }

        @Override
        public V computeIfAbsent(K1 k1, K2 k2, Reference2ReferenceFunction<? super K2, ? extends V> mappingFunction) {
            Map<K2, V> innerMap = innerMapFor(k1);
            if (isInnerRefMap) {
                return ((Reference2ReferenceMap<K2, V>) innerMap).computeIfAbsent(k2, mappingFunction);
            }
            return innerMap.computeIfAbsent(k2, mappingFunction);
        }

        @Override
        public V remove(K1 k1, K2 k2) {
            Map<K2, V> innerMap = map.get(k1);
            if (innerMap == null) return null;
            V removed = innerMap.remove(k2);
            if (removed != null && innerMap.isEmpty()) map.remove(k1);
            return removed;
        }

        @Override
        public Map<K2, V> remove(K1 k1) {
            var innerMap = map.remove(k1);
            if (innerMap != null) return innerMap;
            return Collections.emptyMap();
        }

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public int valueCount() {
            int count = 0;
            for (var innerMap : map.values()) {
                count += innerMap.size();
            }
            return count;
        }

        @Override
        public void forEach(TriConsumer<? super K1, ? super K2, ? super V> action) {
            map.forEach((k1, vs) -> vs.forEach((k2, v) -> action.accept(k1, k2, v)));
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public void clear() {
            map.clear();
        }

        private Map<K2, V> innerMapFor(K1 k1) {
            if (isRefMap) {
                return ((Reference2ReferenceMap<K1, Map<K2, V>>) map).computeIfAbsent(k1, factory);
            }
            return map.computeIfAbsent(k1, factory);
        }
    }
}
