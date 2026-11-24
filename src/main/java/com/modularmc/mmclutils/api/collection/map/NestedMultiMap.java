package com.modularmc.mmclutils.api.collection.map;

import com.modularmc.mmclutils.api.function.TriConsumer;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceFunction;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 按外层键和内层键索引值集合。写入时按需创建缺失的内层映射和值集合。
 * 使用同一实例的读写操作须在同一个线程执行，或由使用方进行同步。
 *
 * @param <K1> 外层键类型
 * @param <K2> 内层键类型
 * @param <V>  值类型
 */
public interface NestedMultiMap<K1, K2, V> {

    /** 包装指定外层映射。工厂每次须提供独立且可修改的内层映射或值集合。 */
    static <K1, K2, V> NestedMultiMap<K1, K2, V> create(
                                                        Map<K1, Map<K2, Collection<V>>> map,
                                                        Supplier<Map<K2, Collection<V>>> mapFactory,
                                                        Supplier<Collection<V>> collectionFactory) {
        return new NestedMultiMapWrapper<>(map, mapFactory, collectionFactory);
    }

    /** 按相等性比较外层键，内层的存储策略由两个工厂提供。 */
    static <K1, K2, V> NestedMultiMap<K1, K2, V> create(
                                                        Supplier<Map<K2, Collection<V>>> mapFactory, Supplier<Collection<V>> collectionFactory) {
        return new NestedMultiMapWrapper<>(new HashMap<>(), mapFactory, collectionFactory);
    }

    /** 按引用身份比较外层键。内层映射和值集合的比较规则由各工厂提供。 */
    static <K1, K2, V> NestedMultiMap<K1, K2, V> createIdentity(
                                                                Supplier<Map<K2, Collection<V>>> mapFactory, Supplier<Collection<V>> collectionFactory) {
        return new NestedMultiMapWrapper<>(new Reference2ReferenceOpenHashMap<>(), mapFactory, collectionFactory);
    }

    /** 返回底层数据的实时视图。通过此视图修改集合会影响本对象。 */
    Map<K1, Map<K2, Collection<V>>> getMap();

    /** 返回指定路径下的原集合。任一级键缺失时返回不可变空列表。 */
    Collection<V> get(K1 k1, K2 k2);

    /** 返回指定外层键的原映射。键缺失时返回不可变空映射。 */
    Map<K2, Collection<V>> get(K1 k1);

    /** 判断是否存在该外层键。 */
    boolean containsKey(K1 k1);

    /** 判断内层是否登记了给定键。 */
    boolean containsKey(K1 k1, K2 k2);

    /** 使用指定路径下的值集合检查给定值。 */
    boolean containsValue(K1 k1, K2 k2, V value);

    /** 插入一个值，按需创建内层映射和值集合。 */
    void put(K1 k1, K2 k2, V value);

    /** 将各元素加入该路径下的集合。重复值按集合自身的规则处理。 */
    void putAll(K1 k1, K2 k2, Collection<V> values);

    /** 删除给定值并返回是否变化。集合或内层映射清空时删除对应键。 */
    boolean remove(K1 k1, K2 k2, V value);

    /** 移除匹配集合中任一项的值。删除后清理变空的路径。 */
    boolean removeAll(K1 k1, K2 k2, Collection<V> values);

    /** 移除指定内层集合并交还其内容。路径缺失时返回不可变空列表。 */
    Collection<V> remove(K1 k1, K2 k2);

    /** 移除外层键并交还内层映射。键缺失时返回不可变空映射。 */
    Map<K2, Collection<V>> remove(K1 k1);

    /** 返回外层键的数量。 */
    int size();

    /** 遍历各层并合计实际存储的值数量。 */
    int valueCount();

    /** 逐个访问外层键、内层键和值。回调中请勿结构性修改当前容器。 */
    void forEach(TriConsumer<? super K1, ? super K2, ? super V> action);

    /** 判断是否还存放外层键。 */
    boolean isEmpty();

    /** 移除所有外层键以及从本映射可访问的值。 */
    void clear();

    /** 使用给定的 Map 和集合工厂存储两级键对应的多值集合。 */
    final class NestedMultiMapWrapper<K1, K2, V> implements NestedMultiMap<K1, K2, V> {

        private final Map<K1, Map<K2, Collection<V>>> map;
        private final Reference2ReferenceFunction<Object, Map<K2, Collection<V>>> mapFactory;
        private final Reference2ReferenceFunction<Object, Collection<V>> collectionFactory;
        private final boolean isRefMap;

        private boolean isInnerRefMap;

        /** 包装现有实时映射。每次调用内层 Map 工厂或集合工厂都应得到独立的可变对象。 */
        public NestedMultiMapWrapper(
                                     Map<K1, Map<K2, Collection<V>>> map,
                                     Supplier<Map<K2, Collection<V>>> mapFactory,
                                     Supplier<Collection<V>> collectionFactory) {
            this.map = map;
            this.collectionFactory = unusedKey -> collectionFactory.get();
            this.mapFactory = unusedKey -> {
                var innerMap = mapFactory.get();
                if (innerMap instanceof Reference2ReferenceMap) {
                    isInnerRefMap = true;
                }
                return innerMap;
            };
            this.isRefMap = map instanceof Reference2ReferenceMap;
        }

        @Override
        public Map<K1, Map<K2, Collection<V>>> getMap() {
            return map;
        }

        @Override
        public Collection<V> get(K1 k1, K2 k2) {
            var innerMap = map.get(k1);
            if (innerMap == null) return Collections.emptyList();
            var values = innerMap.get(k2);
            if (values != null) return values;
            return Collections.emptyList();
        }

        @Override
        public Map<K2, Collection<V>> get(K1 k1) {
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
        public boolean containsValue(K1 k1, K2 k2, V value) {
            var innerMap = map.get(k1);
            if (innerMap == null) return false;
            var values = innerMap.get(k2);
            return values != null && values.contains(value);
        }

        @Override
        public void put(K1 k1, K2 k2, V value) {
            valuesFor(k1, k2).add(value);
        }

        @Override
        public void putAll(K1 k1, K2 k2, Collection<V> values) {
            valuesFor(k1, k2).addAll(values);
        }

        @Override
        public boolean removeAll(K1 k1, K2 k2, Collection<V> values) {
            var innerMap = map.get(k1);
            if (innerMap == null) return false;
            var bound = innerMap.get(k2);
            if (bound == null || !bound.removeAll(values)) {
                return false;
            }
            if (bound.isEmpty()) {
                innerMap.remove(k2);
                if (innerMap.isEmpty()) {
                    map.remove(k1);
                }
            }
            return true;
        }

        @Override
        public boolean remove(K1 k1, K2 k2, V value) {
            var innerMap = map.get(k1);
            if (innerMap == null) return false;
            var values = innerMap.get(k2);
            if (values == null) return false;
            if (values.remove(value)) {
                if (values.isEmpty()) {
                    innerMap.remove(k2);
                    if (innerMap.isEmpty()) {
                        map.remove(k1);
                    }
                }
                return true;
            }
            return false;
        }

        @Override
        public Collection<V> remove(K1 k1, K2 k2) {
            var innerMap = map.get(k1);
            if (innerMap == null) return Collections.emptyList();
            var values = innerMap.remove(k2);
            if (values == null) return Collections.emptyList();
            if (innerMap.isEmpty()) {
                map.remove(k1);
            }
            return values;
        }

        @Override
        public Map<K2, Collection<V>> remove(K1 k1) {
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
                for (var values : innerMap.values()) {
                    count += values.size();
                }
            }
            return count;
        }

        @Override
        public void forEach(TriConsumer<? super K1, ? super K2, ? super V> action) {
            map.forEach((k1, v1s) -> v1s.forEach((k2, v2s) -> v2s.forEach(v -> action.accept(k1, k2, v))));
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public void clear() {
            map.clear();
        }

        private Collection<V> valuesFor(K1 k1, K2 k2) {
            Map<K2, Collection<V>> innerMap;
            if (isRefMap) {
                innerMap = ((Reference2ReferenceMap<K1, Map<K2, Collection<V>>>) map).computeIfAbsent(k1, mapFactory);
            } else {
                innerMap = map.computeIfAbsent(k1, mapFactory);
            }
            if (isInnerRefMap) {
                return ((Reference2ReferenceMap<K2, Collection<V>>) innerMap).computeIfAbsent(k2, collectionFactory);
            }
            return innerMap.computeIfAbsent(k2, collectionFactory);
        }
    }
}
