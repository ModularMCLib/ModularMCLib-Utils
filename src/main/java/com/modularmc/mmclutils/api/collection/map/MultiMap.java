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

/** 实例由单个调用线程使用。 */
public interface MultiMap<K, V> {

    static <K, V> MultiMap<K, V> create(Map<K, Collection<V>> map, Supplier<Collection<V>> factory) {
        return new MultiMapWrapper<>(map, factory);
    }

    static <K, V> MultiMap<K, V> create(Supplier<Collection<V>> factory) {
        return new MultiMapWrapper<>(new HashMap<>(), factory);
    }

    static <K, V> MultiMap<K, V> createIdentity(Supplier<Collection<V>> factory) {
        return new MultiMapWrapper<>(new Reference2ReferenceOpenHashMap<>(), factory);
    }

    Map<K, Collection<V>> getMap();

    Collection<V> get(K key);

    boolean containsKey(K key);

    boolean containsValue(K key, V value);

    void put(K key, V value);

    void putAll(K key, Collection<V> values);

    boolean remove(K key, V value);

    boolean removeAll(K key, Collection<V> values);

    Collection<V> remove(K key);

    int size();

    int valueCount();

    void forEach(BiConsumer<? super K, ? super V> action);

    boolean isEmpty();

    /** 一个键关联多个值，允许由调用方选择底层映射和值集合。 */
    void clear();

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
