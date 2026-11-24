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

/** 实例由单个调用线程使用。 */
public interface NestedMultiMap<K1, K2, V> {

    static <K1, K2, V> NestedMultiMap<K1, K2, V> create(
                                                        Map<K1, Map<K2, Collection<V>>> map,
                                                        Supplier<Map<K2, Collection<V>>> mapFactory,
                                                        Supplier<Collection<V>> collectionFactory) {
        return new NestedMultiMapWrapper<>(map, mapFactory, collectionFactory);
    }

    static <K1, K2, V> NestedMultiMap<K1, K2, V> create(
                                                        Supplier<Map<K2, Collection<V>>> mapFactory, Supplier<Collection<V>> collectionFactory) {
        return new NestedMultiMapWrapper<>(new HashMap<>(), mapFactory, collectionFactory);
    }

    static <K1, K2, V> NestedMultiMap<K1, K2, V> createIdentity(
                                                                Supplier<Map<K2, Collection<V>>> mapFactory, Supplier<Collection<V>> collectionFactory) {
        return new NestedMultiMapWrapper<>(new Reference2ReferenceOpenHashMap<>(), mapFactory, collectionFactory);
    }

    Map<K1, Map<K2, Collection<V>>> getMap();

    Collection<V> get(K1 k1, K2 k2);

    Map<K2, Collection<V>> get(K1 k1);

    boolean containsKey(K1 k1);

    boolean containsKey(K1 k1, K2 k2);

    boolean containsValue(K1 k1, K2 k2, V value);

    void put(K1 k1, K2 k2, V value);

    void putAll(K1 k1, K2 k2, Collection<V> values);

    boolean remove(K1 k1, K2 k2, V value);

    boolean removeAll(K1 k1, K2 k2, Collection<V> values);

    Collection<V> remove(K1 k1, K2 k2);

    Map<K2, Collection<V>> remove(K1 k1);

    int size();

    int valueCount();

    void forEach(TriConsumer<? super K1, ? super K2, ? super V> action);

    boolean isEmpty();

    /** 以两级键索引值集合，并根据需要分配子映射。 */
    void clear();

    final class NestedMultiMapWrapper<K1, K2, V> implements NestedMultiMap<K1, K2, V> {

        private final Map<K1, Map<K2, Collection<V>>> map;
        private final Reference2ReferenceFunction<Object, Map<K2, Collection<V>>> mapFactory;
        private final Reference2ReferenceFunction<Object, Collection<V>> collectionFactory;
        private final boolean isRefMap;

        private boolean isInnerRefMap;

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
