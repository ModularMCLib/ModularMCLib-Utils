package com.modularmc.mmclutils.api.collection.cache;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 支持并发访问的弱键映射。按对象身份比较键，键被回收后由后续访问清理对应的映射。
 * 值由映射强引用。值若持有其键，键会随值保持可达。
 *
 * @param <K> 非空键的类型
 * @param <V> 非空值的类型
 */
@NullMarked
public class ConcurrentWeakIdentityHashMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V> {

    private final ConcurrentMap<Key<K>, V> map;
    private final ReferenceQueue<K> queue = new ReferenceQueue<>();
    private transient @Nullable Set<Map.Entry<K, V>> entrySet;

    /**
     * 按预计元素数量创建空映射。
     *
     * @param initialCapacity 预计同时保存的键数量，须为非负数
     * @throws IllegalArgumentException 初始容量为负数
     */
    public ConcurrentWeakIdentityHashMap(int initialCapacity) {
        this.map = new ConcurrentHashMap<>(initialCapacity);
    }

    /** 使用并发映射的默认初始容量。 */
    public ConcurrentWeakIdentityHashMap() {
        this.map = new ConcurrentHashMap<>();
    }

    @Override
    public @Nullable V get(Object key) {
        purgeKeys();
        return map.get(new Key<>(key, null));
    }

    @Override
    public @Nullable V put(K key, V value) {
        purgeKeys();
        return map.put(new Key<>(key, queue), value);
    }

    @Override
    public int size() {
        purgeKeys();
        return map.size();
    }

    @SuppressWarnings({ "ReassignedVariable", "SuspiciousMethodCalls" })
    private void purgeKeys() {
        Reference<? extends K> reference;
        while ((reference = queue.poll()) != null) {
            map.remove(reference);
        }
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        Set<Map.Entry<K, V>> entrySet = this.entrySet;
        return entrySet == null ? this.entrySet = new EntrySet() : entrySet;
    }

    @Override
    public @Nullable V putIfAbsent(K key, V value) {
        purgeKeys();
        return map.putIfAbsent(new Key<>(key, queue), value);
    }

    @Override
    public @Nullable V remove(Object key) {
        return map.remove(new Key<>(key, null));
    }

    @Override
    public boolean remove(Object key, Object value) {
        purgeKeys();
        return map.remove(new Key<>(key, null), value);
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        purgeKeys();
        return map.replace(new Key<>(key, null), oldValue, newValue);
    }

    @Override
    public @Nullable V replace(K key, V value) {
        purgeKeys();
        return map.replace(new Key<>(key, null), value);
    }

    @Override
    public boolean containsKey(Object key) {
        purgeKeys();
        return map.containsKey(new Key<>(key, null));
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public void clear() {
        while (queue.poll() != null);
        map.clear();
    }

    @Override
    public boolean containsValue(Object value) {
        purgeKeys();
        return map.containsValue(value);
    }

    private static class Key<T> extends WeakReference<T> {

        private final int hash;

        Key(T t, @Nullable ReferenceQueue<T> queue) {
            super(t, queue);
            if (t == null) {
                throw new NullPointerException("key");
            }
            hash = System.identityHashCode(t);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            T value = get();
            return value != null && obj instanceof Key<?> key && key.get() == value;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private class Iter implements Iterator<Map.Entry<K, V>> {

        private final Iterator<Map.Entry<Key<K>, V>> it;
        private Map.@Nullable Entry<K, V> nextValue;
        private @Nullable K lastKey;

        Iter(Iterator<Map.Entry<Key<K>, V>> it) {
            this.it = it;
        }

        @Override
        public boolean hasNext() {
            if (nextValue != null) {
                return true;
            }
            while (it.hasNext()) {
                Map.Entry<Key<K>, V> entry = it.next();
                K key = entry.getKey().get();
                if (key != null) {
                    nextValue = new Entry(key, entry.getValue());
                    return true;
                } else {
                    it.remove();
                }
            }
            return false;
        }

        @Override
        public Map.Entry<K, V> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            Map.Entry<K, V> entry = nextValue;
            nextValue = null;
            lastKey = entry.getKey();
            return entry;
        }

        @Override
        public void remove() {
            if (lastKey == null) throw new IllegalStateException();
            ConcurrentWeakIdentityHashMap.this.remove(lastKey);
            lastKey = null;
        }
    }

    private class EntrySet extends AbstractSet<Map.Entry<K, V>> {

        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return new Iter(map.entrySet().iterator());
        }

        @Override
        public int size() {
            return ConcurrentWeakIdentityHashMap.this.size();
        }

        @Override
        public void clear() {
            ConcurrentWeakIdentityHashMap.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) {
                return false;
            }
            return e.getValue() != null && ConcurrentWeakIdentityHashMap.this.get(e.getKey()) == e.getValue();
        }

        @Override
        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) {
                return false;
            }
            return ConcurrentWeakIdentityHashMap.this.remove(e.getKey(), e.getValue());
        }
    }

    private class Entry extends AbstractMap.SimpleEntry<K, V> {

        @Serial
        private static final long serialVersionUID = 1L;

        Entry(K key, V value) {
            super(key, value);
        }

        @Override
        public V setValue(V value) {
            ConcurrentWeakIdentityHashMap.this.put(getKey(), value);
            return super.setValue(value);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Map.Entry<?, ?> e) {
                return getKey() == e.getKey() && getValue() == e.getValue();
            }
            return false;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(getKey()) ^ System.identityHashCode(getValue());
        }
    }
}
