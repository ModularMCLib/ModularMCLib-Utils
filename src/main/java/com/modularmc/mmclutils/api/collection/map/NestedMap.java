package com.modularmc.mmclutils.api.collection.map;

import com.modularmc.mmclutils.api.function.TriConsumer;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceFunction;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** 实例由单个调用线程使用。 */
public interface NestedMap<K1, K2, V> {

    static <K1, K2, V> NestedMap<K1, K2, V> create(Map<K1, Map<K2, V>> map, Supplier<Map<K2, V>> factory) {
        return new NestedMapWrapper<>(map, factory);
    }

    static <K1, K2, V> NestedMap<K1, K2, V> create(Supplier<Map<K2, V>> factory) {
        return new NestedMapWrapper<>(new HashMap<>(), factory);
    }

    static <K1, K2, V> NestedMap<K1, K2, V> createIdentity(Supplier<Map<K2, V>> factory) {
        return new NestedMapWrapper<>(new Reference2ReferenceOpenHashMap<>(), factory);
    }

    Map<K1, Map<K2, V>> getMap();

    V get(K1 k1, K2 k2);

    Map<K2, V> get(K1 k1);

    boolean containsKey(K1 k1);

    boolean containsKey(K1 k1, K2 k2);

    V put(K1 k1, K2 k2, V value);

    V computeIfAbsent(K1 k1, K2 k2, Reference2ReferenceFunction<? super K2, ? extends V> mappingFunction);

    V remove(K1 k1, K2 k2);

    Map<K2, V> remove(K1 k1);

    int size();

    int valueCount();

    void forEach(TriConsumer<? super K1, ? super K2, ? super V> action);

    boolean isEmpty();

    /** 以两级键索引单个值，允许调用方选择各层底层映射。 */
    void clear();

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
