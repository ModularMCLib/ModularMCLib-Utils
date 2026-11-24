package com.modularmc.mmclutils.api.collection.map.hash;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.*;
import java.util.Map;
import java.util.Spliterator;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;
import static it.unimi.dsi.fastutil.HashCommon.maxFill;

/** 缓存键哈希的开放寻址容器。先比较哈希和对象身份，再检查键的相等性。 扩容时复用哈希值，实例由单个线程使用。 */
public final class Object2ObjectOpenCachedHashMap<K, V> extends Object2ObjectOpenHashMap<K, V> {

    private int[] hash;

    @Serial
    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
        input.defaultReadObject();
        // fastutil 按读入后的槽位重建哈希表，缓存哈希要与新的槽位对齐。
        hash = new int[n + 1];
        for (int i = 0; i < n; i++) {
            K entry = key[i];
            if (entry != null) hash[i] = entry.hashCode();
        }
    }

    /**
     * 创建空映射。
     *
     * @param expected 预计存放的键数
     * @param f        触发扩容的负载因子
     */
    public Object2ObjectOpenCachedHashMap(final int expected, final float f) {
        super(expected, f);
        hash = new int[n + 1];
    }

    /**
     * 创建空映射。 使用默认负载因子。
     *
     * @param expected 预计存放的键数
     */
    public Object2ObjectOpenCachedHashMap(final int expected) {
        super(expected, DEFAULT_LOAD_FACTOR);
        hash = new int[n + 1];
    }

    /** 创建空映射。 使用默认负载因子。 */
    public Object2ObjectOpenCachedHashMap() {
        super(DEFAULT_INITIAL_SIZE, DEFAULT_LOAD_FACTOR);
        hash = new int[n + 1];
    }

    /**
     * 从给定映射复制条目。
     *
     * @param m 复制条目的来源映射
     * @param f 触发扩容的负载因子
     */
    public Object2ObjectOpenCachedHashMap(final Map<? extends K, ? extends V> m, final float f) {
        super(m.size(), f);
        hash = new int[n + 1];
        putAll(m);
    }

    /**
     * 从给定映射复制条目。 使用默认负载因子。
     *
     * @param m 复制条目的来源映射
     */
    public Object2ObjectOpenCachedHashMap(final Map<? extends K, ? extends V> m) {
        this(m, DEFAULT_LOAD_FACTOR);
    }

    /**
     * 从给定映射复制条目。
     *
     * @param m 复制条目的来源映射
     * @param f 触发扩容的负载因子
     */
    public Object2ObjectOpenCachedHashMap(final Object2ObjectMap<K, V> m, final float f) {
        super(m.size(), f);
        hash = new int[n + 1];
        putAll(m);
    }

    /**
     * 从给定映射复制条目。 使用默认负载因子。
     *
     * @param m 复制条目的来源映射
     */
    public Object2ObjectOpenCachedHashMap(final Object2ObjectMap<K, V> m) {
        this(m, DEFAULT_LOAD_FACTOR);
    }

    private V removeEntry(int pos) {
        final V oldValue = value[pos];
        value[pos] = null;
        size--;
        int last, slot, ch;
        K curr;
        final K[] key = this.key;
        final V[] value = this.value;
        final int[] hash = this.hash;
        final int mask = this.mask;
        a:
        for (;;) {
            pos = ((last = pos) + 1) & mask;
            for (;;) {
                if ((curr = key[pos]) == null) {
                    key[last] = null;
                    value[last] = null;
                    hash[last] = 0;
                    break a;
                }
                ch = hash[pos];
                slot = HashCommon.mix(ch) & mask;
                if (last <= pos ? last >= slot || slot > pos : last >= slot && slot > pos) break;
                pos = (pos + 1) & mask;
            }
            key[last] = curr;
            value[last] = value[pos];
            hash[last] = ch;
        }
        if (n > minN && size < maxFill / 4 && n > DEFAULT_INITIAL_SIZE) rehash(n / 2);
        return oldValue;
    }

    private V removeNullEntry() {
        containsNullKey = false;
        key[n] = null;
        hash[n] = 0;
        final V oldValue = value[n];
        value[n] = null;
        size--;
        if (n > minN && size < maxFill / 4 && n > DEFAULT_INITIAL_SIZE) rehash(n / 2);
        return oldValue;
    }

    private int find(final K k, final int h) {
        K curr;
        final K[] key = this.key;
        final int[] hash = this.hash;
        final int mask = this.mask;
        int pos;
        if ((curr = key[pos = HashCommon.mix(h) & mask]) == null) return -(pos + 1);
        if (hash[pos] == h && (k == curr || k.equals(curr))) return pos;
        while (true) {
            if ((curr = key[pos = (pos + 1) & mask]) == null) return -(pos + 1);
            if (hash[pos] == h && (k == curr || k.equals(curr))) return pos;
        }
    }

    private void insert(final int pos, final K k, final V v, final int h) {
        if (pos == n) containsNullKey = true;
        key[pos] = k;
        value[pos] = v;
        hash[pos] = h;
        if (size++ >= maxFill) rehash(arraySize(size + 1, f));
    }

    @Override
    public V put(final K k, final V v) {
        int pos, h = 0;
        if (k == null) {
            pos = containsNullKey ? n : -(n + 1);
        } else {
            h = k.hashCode();
            pos = find(k, h);
        }
        if (pos < 0) {
            insert(-pos - 1, k, v, h);
            return defRetValue;
        }
        final V oldValue = value[pos];
        value[pos] = v;
        return oldValue;
    }

    @Override
    public V remove(final Object k) {
        if (k == null) {
            if (containsNullKey) return removeNullEntry();
            return defRetValue;
        }
        K curr;
        final K[] key = this.key;
        final int[] hash = this.hash;
        final int mask = this.mask;
        final int h = k.hashCode();
        int pos;
        if ((curr = key[pos = HashCommon.mix(h) & mask]) == null) return defRetValue;
        if (hash[pos] == h && (k == curr || k.equals(curr))) return removeEntry(pos);
        while (true) {
            if ((curr = key[pos = (pos + 1) & mask]) == null) return defRetValue;
            if (hash[pos] == h && (k == curr || k.equals(curr))) return removeEntry(pos);
        }
    }

    @Override
    public V get(final Object k) {
        if (k == null) return containsNullKey ? value[n] : defRetValue;
        K curr;
        final K[] key = this.key;
        final int[] hash = this.hash;
        final int mask = this.mask;
        final int h = k.hashCode();
        int pos;
        if ((curr = key[pos = HashCommon.mix(h) & mask]) == null) return defRetValue;
        if (hash[pos] == h && (k == curr || k.equals(curr))) return value[pos];
        while (true) {
            if ((curr = key[pos = (pos + 1) & mask]) == null) return defRetValue;
            if (hash[pos] == h && (k == curr || k.equals(curr))) return value[pos];
        }
    }

    @Override
    public boolean containsKey(final Object k) {
        if (k == null) return containsNullKey;
        K curr;
        final K[] key = this.key;
        final int[] hash = this.hash;
        final int mask = this.mask;
        final int h = k.hashCode();
        int pos;
        if ((curr = key[pos = HashCommon.mix(h) & mask]) == null) return false;
        if (hash[pos] == h && (k == curr || k.equals(curr))) return true;
        while (true) {
            if ((curr = key[pos = (pos + 1) & mask]) == null) return false;
            if (hash[pos] == h && (k == curr || k.equals(curr))) return true;
        }
    }

    @Override
    public V getOrDefault(final Object k, final V defaultValue) {
        if (k == null) return containsNullKey ? value[n] : defaultValue;
        K curr;
        final K[] key = this.key;
        final int[] hash = this.hash;
        final int mask = this.mask;
        final int h = k.hashCode();
        int pos;
        if ((curr = key[pos = HashCommon.mix(h) & mask]) == null) return defaultValue;
        if (hash[pos] == h && (k == curr || k.equals(curr))) return value[pos];
        while (true) {
            if ((curr = key[pos = (pos + 1) & mask]) == null) return defaultValue;
            if (hash[pos] == h && (k == curr || k.equals(curr))) return value[pos];
        }
    }

    @Override
    public V putIfAbsent(final K k, final V v) {
        int pos, h = 0;
        if (k == null) {
            pos = containsNullKey ? n : -(n + 1);
        } else {
            h = k.hashCode();
            pos = find(k, h);
        }
        if (pos >= 0) return value[pos];
        insert(-pos - 1, k, v, h);
        return defRetValue;
    }

    @Override
    public boolean remove(final Object k, final Object v) {
        if (k == null) {
            if (containsNullKey && Objects.equals(v, value[n])) {
                removeNullEntry();
                return true;
            }
            return false;
        }
        K curr;
        final K[] key = this.key;
        final int[] hash = this.hash;
        final int mask = this.mask;
        final int h = k.hashCode();
        int pos;
        if ((curr = key[pos = HashCommon.mix(h) & mask]) == null) return false;
        if (hash[pos] == h && (k == curr || k.equals(curr)) && Objects.equals(v, value[pos])) {
            removeEntry(pos);
            return true;
        }
        while (true) {
            if ((curr = key[pos = (pos + 1) & mask]) == null) return false;
            if (hash[pos] == h && (k == curr || k.equals(curr)) && Objects.equals(v, value[pos])) {
                removeEntry(pos);
                return true;
            }
        }
    }

    @Override
    public boolean replace(final K k, final V oldValue, final V v) {
        int pos;
        if (k == null) {
            pos = containsNullKey ? n : -(n + 1);
        } else {
            int h = k.hashCode();
            pos = find(k, h);
        }
        if (pos < 0 || !Objects.equals(oldValue, value[pos])) return false;
        value[pos] = v;
        return true;
    }

    @Override
    public V replace(final K k, final V v) {
        int pos;
        if (k == null) {
            pos = containsNullKey ? n : -(n + 1);
        } else {
            int h = k.hashCode();
            pos = find(k, h);
        }
        if (pos < 0) return defRetValue;
        final V oldValue = value[pos];
        value[pos] = v;
        return oldValue;
    }

    @Override
    public V computeIfAbsent(K k, Function<? super K, ? extends V> mappingFunction) {
        int pos, h = 0;
        if (k == null) {
            pos = containsNullKey ? n : -(n + 1);
        } else {
            h = k.hashCode();
            pos = find(k, h);
        }
        if (pos >= 0) return value[pos];
        final V newValue = mappingFunction.apply(k);
        insert(-pos - 1, k, newValue, h);
        return newValue;
    }

    @Override
    public V computeIfAbsent(final K k, final Object2ObjectFunction<? super K, ? extends V> mappingFunction) {
        int pos, h = 0;
        if (k == null) {
            pos = containsNullKey ? n : -(n + 1);
        } else {
            h = k.hashCode();
            pos = find(k, h);
        }
        if (pos >= 0) return value[pos];
        if (!mappingFunction.containsKey(k)) return defRetValue;
        final V newValue = mappingFunction.get(k);
        insert(-pos - 1, k, newValue, h);
        return newValue;
    }

    @Override
    public V computeIfPresent(final K k, final BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        int pos;
        if (k == null) {
            pos = containsNullKey ? n : -(n + 1);
        } else {
            int h = k.hashCode();
            pos = find(k, h);
        }
        if (pos < 0) return defRetValue;
        if (value[pos] == null) return defRetValue;
        final V newValue = remappingFunction.apply((k), (value[pos]));
        if (newValue == null) {
            if (k == null) removeNullEntry();
            else removeEntry(pos);
            return defRetValue;
        }
        return value[pos] = (newValue);
    }

    @Override
    public V compute(final K k, final BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        int pos, h = 0;
        if (k == null) {
            pos = containsNullKey ? n : -(n + 1);
        } else {
            h = k.hashCode();
            pos = find(k, h);
        }
        final V newValue = remappingFunction.apply((k), pos >= 0 ? (value[pos]) : null);
        if (newValue == null) {
            if (pos >= 0) {
                if (k == null) removeNullEntry();
                else removeEntry(pos);
            }
            return defRetValue;
        }
        if (pos < 0) {
            insert(-pos - 1, k, newValue, h);
            return newValue;
        }
        return value[pos] = newValue;
    }

    @Override
    public V merge(final K k, final V v, final BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        int pos, h = 0;
        if (k == null) {
            pos = containsNullKey ? n : -(n + 1);
        } else {
            h = k.hashCode();
            pos = find(k, h);
        }
        if (pos < 0 || value[pos] == null) {
            if (pos < 0) insert(-pos - 1, k, v, h);
            else value[pos] = v;
            return v;
        }
        final V newValue = remappingFunction.apply((value[pos]), (v));
        if (newValue == null) {
            if (k == null) removeNullEntry();
            else removeEntry(pos);
            return defRetValue;
        }
        return value[pos] = newValue;
    }

    @Override
    public void clear() {
        if (size == 0) return;
        size = 0;
        containsNullKey = false;
        Arrays.fill(key, (null));
        Arrays.fill(value, null);
        Arrays.fill(hash, 0);
    }

    final class MapEntry implements Entry<K, V>, Pair<K, V> {

        int index;

        MapEntry(final int index) {
            this.index = index;
        }

        MapEntry() {}

        @Override
        public K getKey() {
            return key[index];
        }

        @Override
        public K left() {
            return key[index];
        }

        @Override
        public V getValue() {
            return value[index];
        }

        @Override
        public V right() {
            return value[index];
        }

        @Override
        public V setValue(final V v) {
            final V oldValue = value[index];
            value[index] = v;
            return oldValue;
        }

        @Override
        public Pair<K, V> right(final V v) {
            value[index] = v;
            return this;
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean equals(final Object o) {
            if (!(o instanceof Map.Entry)) return false;
            Map.Entry<K, V> e = (Map.Entry<K, V>) o;
            return Objects.equals(key[index], (e.getKey())) && Objects.equals(value[index], (e.getValue()));
        }

        @Override
        public int hashCode() {
            return hash[index] ^ (value[index] == null ? 0 : value[index].hashCode());
        }

        @Override
        public String toString() {
            return key[index] + "=>" + value[index];
        }
    }

    private abstract class MapIterator<ConsumerType> {

        int pos = n;
        int last = -1;
        int c = size;
        boolean mustReturnNullKey = Object2ObjectOpenCachedHashMap.this.containsNullKey;
        ObjectArrayList<K> wrapped;

        abstract void acceptOnIndex(final ConsumerType action, final int index);

        public boolean hasNext() {
            return c != 0;
        }

        public int nextEntry() {
            c--;
            if (mustReturnNullKey) {
                mustReturnNullKey = false;
                return last = n;
            }
            final K[] key = Object2ObjectOpenCachedHashMap.this.key;
            final int[] hash = Object2ObjectOpenCachedHashMap.this.hash;
            final int mask = Object2ObjectOpenCachedHashMap.this.mask;
            for (;;) {
                if (--pos < 0) {
                    last = Integer.MIN_VALUE;
                    final K k = wrapped.get(-pos - 1);
                    final int h = k.hashCode();
                    int p = HashCommon.mix(h) & mask;
                    while (!(hash[p] == h && k.equals(key[p]))) p = (p + 1) & mask;
                    return p;
                }
                if (!((key[pos]) == null)) return last = pos;
            }
        }

        public void forEachRemaining(final ConsumerType action) {
            if (mustReturnNullKey) {
                mustReturnNullKey = false;
                acceptOnIndex(action, last = n);
                c--;
            }
            final K[] key = Object2ObjectOpenCachedHashMap.this.key;
            final int[] hash = Object2ObjectOpenCachedHashMap.this.hash;
            while (c != 0) {
                if (--pos < 0) {
                    last = Integer.MIN_VALUE;
                    final K k = wrapped.get(-pos - 1);
                    final int h = k.hashCode();
                    int p = HashCommon.mix(h) & mask;
                    while (!(hash[p] == h && k.equals(key[p]))) p = (p + 1) & mask;
                    acceptOnIndex(action, p);
                    c--;
                } else if (!((key[pos]) == null)) {
                    acceptOnIndex(action, last = pos);
                    c--;
                }
            }
        }

        private void shiftKeys(int pos) {
            int last, slot, ch;
            K curr;
            final K[] key = Object2ObjectOpenCachedHashMap.this.key;
            final V[] value = Object2ObjectOpenCachedHashMap.this.value;
            final int[] hash = Object2ObjectOpenCachedHashMap.this.hash;
            final int mask = Object2ObjectOpenCachedHashMap.this.mask;
            for (;;) {
                pos = ((last = pos) + 1) & mask;
                for (;;) {
                    if ((curr = key[pos]) == null) {
                        key[last] = (null);
                        value[last] = null;
                        hash[last] = 0;
                        return;
                    }
                    ch = hash[pos];
                    slot = HashCommon.mix(ch) & mask;
                    if (last <= pos ? last >= slot || slot > pos : last >= slot && slot > pos) break;
                    pos = (pos + 1) & mask;
                }
                if (pos < last) {
                    if (wrapped == null) wrapped = new ObjectArrayList<>(2);
                    wrapped.add(key[pos]);
                }
                key[last] = curr;
                value[last] = value[pos];
                hash[last] = ch;
            }
        }

        public void remove() {
            if (last == n) {
                containsNullKey = false;
                key[n] = null;
                hash[n] = 0;
                value[n] = null;
            } else if (pos >= 0) shiftKeys(last);
            else {
                Object2ObjectOpenCachedHashMap.this.remove(wrapped.set(-pos - 1, null));
                last = -1;
                return;
            }
            size--;
            last = -1;
        }

        public int skip(final int n) {
            int i = n;
            while (i-- != 0 && hasNext()) nextEntry();
            return n - i - 1;
        }
    }

    private final class EntryIterator extends MapIterator<Consumer<? super Entry<K, V>>> implements ObjectIterator<Entry<K, V>> {

        private MapEntry entry;

        @Override
        public MapEntry next() {
            return entry = new MapEntry(nextEntry());
        }

        @Override
        void acceptOnIndex(final Consumer<? super Entry<K, V>> action, final int index) {
            action.accept(entry = new MapEntry(index));
        }

        @Override
        public void remove() {
            super.remove();
            entry.index = -1;
        }
    }

    private final class FastEntryIterator extends MapIterator<Consumer<? super Entry<K, V>>> implements ObjectIterator<Entry<K, V>> {

        private final MapEntry entry = new MapEntry();

        @Override
        public MapEntry next() {
            entry.index = nextEntry();
            return entry;
        }

        @Override
        void acceptOnIndex(final Consumer<? super Entry<K, V>> action, final int index) {
            entry.index = index;
            action.accept(entry);
        }
    }

    private abstract class MapSpliterator<ConsumerType, SplitType extends MapSpliterator<ConsumerType, SplitType>> {

        int pos = 0;
        int max = n;
        int c = 0;
        boolean mustReturnNull = Object2ObjectOpenCachedHashMap.this.containsNullKey;
        boolean hasSplit = false;

        MapSpliterator() {}

        MapSpliterator(int pos, int max, boolean mustReturnNull, boolean hasSplit) {
            this.pos = pos;
            this.max = max;
            this.mustReturnNull = mustReturnNull;
            this.hasSplit = hasSplit;
        }

        abstract void acceptOnIndex(final ConsumerType action, final int index);

        abstract SplitType makeForSplit(int pos, int max, boolean mustReturnNull);

        public boolean tryAdvance(final ConsumerType action) {
            if (mustReturnNull) {
                mustReturnNull = false;
                ++c;
                acceptOnIndex(action, n);
                return true;
            }
            final K[] key = Object2ObjectOpenCachedHashMap.this.key;
            while (pos < max) {
                if (!((key[pos]) == null)) {
                    ++c;
                    acceptOnIndex(action, pos++);
                    return true;
                }
                ++pos;
            }
            return false;
        }

        public void forEachRemaining(final ConsumerType action) {
            if (mustReturnNull) {
                mustReturnNull = false;
                ++c;
                acceptOnIndex(action, n);
            }
            final K[] key = Object2ObjectOpenCachedHashMap.this.key;
            while (pos < max) {
                if (!((key[pos]) == null)) {
                    acceptOnIndex(action, pos);
                    ++c;
                }
                ++pos;
            }
        }

        public long estimateSize() {
            if (!hasSplit) {
                return size - c;
            } else {
                return Math.min(size - c, (long) (((double) (containsNullKey ? size - 1 : size) / n) * (max - pos)) + (mustReturnNull ? 1 : 0));
            }
        }

        public SplitType trySplit() {
            if (pos >= max - 1) return null;
            int retLen = (max - pos) >> 1;
            if (retLen <= 1) return null;
            int myNewPos = pos + retLen;
            int retPos = pos;
            SplitType split = makeForSplit(retPos, myNewPos, mustReturnNull);
            this.pos = myNewPos;
            this.mustReturnNull = false;
            this.hasSplit = true;
            return split;
        }

        public long skip(long n) {
            if (n == 0) return 0;
            long skipped = 0;
            if (mustReturnNull) {
                mustReturnNull = false;
                ++skipped;
                --n;
            }
            final K[] key = Object2ObjectOpenCachedHashMap.this.key;
            while (pos < max && n > 0) {
                if (!((key[pos++]) == null)) {
                    ++skipped;
                    --n;
                }
            }
            return skipped;
        }
    }

    private final class EntrySpliterator extends MapSpliterator<Consumer<? super Entry<K, V>>, EntrySpliterator> implements ObjectSpliterator<Entry<K, V>> {

        private static final int POST_SPLIT_CHARACTERISTICS = ObjectSpliterators.SET_SPLITERATOR_CHARACTERISTICS & ~Spliterator.SIZED;

        EntrySpliterator() {}

        EntrySpliterator(int pos, int max, boolean mustReturnNull, boolean hasSplit) {
            super(pos, max, mustReturnNull, hasSplit);
        }

        @Override
        public int characteristics() {
            return hasSplit ? POST_SPLIT_CHARACTERISTICS : ObjectSpliterators.SET_SPLITERATOR_CHARACTERISTICS;
        }

        @Override
        void acceptOnIndex(final Consumer<? super Entry<K, V>> action, final int index) {
            action.accept(new MapEntry(index));
        }

        @Override
        EntrySpliterator makeForSplit(int pos, int max, boolean mustReturnNull) {
            return new EntrySpliterator(pos, max, mustReturnNull, true);
        }
    }

    private final class MapEntrySet extends AbstractObjectSet<Entry<K, V>> implements FastEntrySet<K, V> {

        @Override
        public ObjectIterator<Entry<K, V>> iterator() {
            return new EntryIterator();
        }

        @Override
        public ObjectIterator<Entry<K, V>> fastIterator() {
            return new FastEntryIterator();
        }

        @Override
        public ObjectSpliterator<Entry<K, V>> spliterator() {
            return new EntrySpliterator();
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            if (c instanceof Object2ObjectMap.FastEntrySet set) {
                for (ObjectIterator it = set.fastIterator(); it.hasNext();) if (!contains(it.next())) return false;
            } else {
                for (Object e : c) if (!contains(e)) return false;
            }
            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean contains(final Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            final K k = ((K) e.getKey());
            final V v = ((V) e.getValue());
            if (k == null) return Object2ObjectOpenCachedHashMap.this.containsNullKey && Objects.equals(value[n], v);
            K curr;
            final K[] key = Object2ObjectOpenCachedHashMap.this.key;
            final int[] hash = Object2ObjectOpenCachedHashMap.this.hash;
            final int mask = Object2ObjectOpenCachedHashMap.this.mask;
            final int h = k.hashCode();
            int pos;
            if ((curr = key[pos = HashCommon.mix(h) & mask]) == null) return false;
            if (h == hash[pos] && k.equals(curr)) return Objects.equals(value[pos], v);
            while (true) {
                if ((curr = key[pos = (pos + 1) & mask]) == null) return false;
                if (h == hash[pos] && k.equals(curr)) return Objects.equals(value[pos], v);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean remove(final Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            final K k = ((K) e.getKey());
            final V v = ((V) e.getValue());
            if (k == null) {
                if (containsNullKey && Objects.equals(value[n], v)) {
                    removeNullEntry();
                    return true;
                }
                return false;
            }
            K curr;
            final K[] key = Object2ObjectOpenCachedHashMap.this.key;
            final int[] hash = Object2ObjectOpenCachedHashMap.this.hash;
            final int mask = Object2ObjectOpenCachedHashMap.this.mask;
            final int h = k.hashCode();
            int pos;
            if ((curr = key[pos = HashCommon.mix(h) & mask]) == null) return false;
            if (h == hash[pos] && k.equals(curr)) {
                if (Objects.equals(value[pos], v)) {
                    removeEntry(pos);
                    return true;
                }
                return false;
            }
            while (true) {
                if ((curr = key[pos = (pos + 1) & mask]) == null) return false;
                if (h == hash[pos] && k.equals(curr)) {
                    if (Objects.equals(value[pos], v)) {
                        removeEntry(pos);
                        return true;
                    }
                }
            }
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void clear() {
            Object2ObjectOpenCachedHashMap.this.clear();
        }

        @Override
        public void forEach(final Consumer<? super Entry<K, V>> consumer) {
            if (containsNullKey) consumer.accept(new MapEntry(n));
            final K[] key = Object2ObjectOpenCachedHashMap.this.key;
            for (int pos = n; pos-- != 0;) if (!((key[pos]) == null)) consumer.accept(new MapEntry(pos));
        }

        @Override
        public void fastForEach(final Consumer<? super Entry<K, V>> consumer) {
            final MapEntry entry = new MapEntry();
            if (containsNullKey) {
                entry.index = n;
                consumer.accept(entry);
            }
            final K[] key = Object2ObjectOpenCachedHashMap.this.key;
            for (int pos = n; pos-- != 0;)
                if (!((key[pos]) == null)) {
                    entry.index = pos;
                    consumer.accept(entry);
                }
        }
    }

    @Override
    public FastEntrySet<K, V> object2ObjectEntrySet() {
        if (entries == null) entries = new MapEntrySet();
        return entries;
    }

    private final class KeyIterator extends MapIterator<Consumer<? super K>> implements ObjectIterator<K> {

        public KeyIterator() {
            super();
        }

        @Override
        void acceptOnIndex(final Consumer<? super K> action, final int index) {
            action.accept(key[index]);
        }

        @Override
        public K next() {
            return key[nextEntry()];
        }
    }

    private final class KeySpliterator extends MapSpliterator<Consumer<? super K>, KeySpliterator> implements ObjectSpliterator<K> {

        private static final int POST_SPLIT_CHARACTERISTICS = ObjectSpliterators.SET_SPLITERATOR_CHARACTERISTICS & ~Spliterator.SIZED;

        KeySpliterator() {}

        KeySpliterator(int pos, int max, boolean mustReturnNull, boolean hasSplit) {
            super(pos, max, mustReturnNull, hasSplit);
        }

        @Override
        public int characteristics() {
            return hasSplit ? POST_SPLIT_CHARACTERISTICS : ObjectSpliterators.SET_SPLITERATOR_CHARACTERISTICS;
        }

        @Override
        void acceptOnIndex(final Consumer<? super K> action, final int index) {
            action.accept(key[index]);
        }

        @Override
        KeySpliterator makeForSplit(int pos, int max, boolean mustReturnNull) {
            return new KeySpliterator(pos, max, mustReturnNull, true);
        }
    }

    private final class KeySet extends AbstractObjectSet<K> {

        @Override
        public ObjectIterator<K> iterator() {
            return new KeyIterator();
        }

        @Override
        public ObjectSpliterator<K> spliterator() {
            return new KeySpliterator();
        }

        @Override
        public void forEach(final Consumer<? super K> consumer) {
            final K[] key = Object2ObjectOpenCachedHashMap.this.key;
            if (containsNullKey) consumer.accept(key[n]);
            for (int pos = n; pos-- != 0;) {
                final K k = key[pos];
                if (!((k) == null)) consumer.accept(k);
            }
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean contains(Object k) {
            return containsKey(k);
        }

        @Override
        public boolean remove(Object k) {
            final int oldSize = size;
            Object2ObjectOpenCachedHashMap.this.remove(k);
            return size != oldSize;
        }

        @Override
        public void clear() {
            Object2ObjectOpenCachedHashMap.this.clear();
        }
    }

    @Override
    public ObjectSet<K> keySet() {
        if (keys == null) keys = new KeySet();
        return keys;
    }

    private final class ValueIterator extends MapIterator<Consumer<? super V>> implements ObjectIterator<V> {

        public ValueIterator() {
            super();
        }

        @Override
        void acceptOnIndex(final Consumer<? super V> action, final int index) {
            action.accept(value[index]);
        }

        @Override
        public V next() {
            return value[nextEntry()];
        }
    }

    private final class ValueSpliterator extends MapSpliterator<Consumer<? super V>, ValueSpliterator> implements ObjectSpliterator<V> {

        private static final int POST_SPLIT_CHARACTERISTICS = ObjectSpliterators.COLLECTION_SPLITERATOR_CHARACTERISTICS & ~Spliterator.SIZED;

        ValueSpliterator() {}

        ValueSpliterator(int pos, int max, boolean mustReturnNull, boolean hasSplit) {
            super(pos, max, mustReturnNull, hasSplit);
        }

        @Override
        public int characteristics() {
            return hasSplit ? POST_SPLIT_CHARACTERISTICS : ObjectSpliterators.COLLECTION_SPLITERATOR_CHARACTERISTICS;
        }

        @Override
        void acceptOnIndex(final Consumer<? super V> action, final int index) {
            action.accept(value[index]);
        }

        @Override
        ValueSpliterator makeForSplit(int pos, int max, boolean mustReturnNull) {
            return new ValueSpliterator(pos, max, mustReturnNull, true);
        }
    }

    @Override
    public ObjectCollection<V> values() {
        if (values == null) values = new AbstractObjectCollection<>() {

            @Override
            public ObjectIterator<V> iterator() {
                return new ValueIterator();
            }

            @Override
            public ObjectSpliterator<V> spliterator() {
                return new ValueSpliterator();
            }

            @Override
            public void forEach(final Consumer<? super V> consumer) {
                final K[] key = Object2ObjectOpenCachedHashMap.this.key;
                final V[] value = Object2ObjectOpenCachedHashMap.this.value;
                if (containsNullKey) consumer.accept(value[n]);
                for (int pos = n; pos-- != 0;) if (!((key[pos]) == null)) consumer.accept(value[pos]);
            }

            @Override
            public int size() {
                return size;
            }

            @Override
            public boolean contains(Object v) {
                return containsValue(v);
            }

            @Override
            public void clear() {
                Object2ObjectOpenCachedHashMap.this.clear();
            }
        };
        return values;
    }

    @SuppressWarnings("unchecked")
    protected void rehash(final int newN) {
        final K[] key = this.key;
        final V[] value = this.value;
        final int[] hash = this.hash;
        final int mask = newN - 1;
        final K[] newKey = (K[]) new Object[newN + 1];
        final V[] newValue = (V[]) new Object[newN + 1];
        final int[] newHash = new int[newN + 1];
        int i = n, pos, h;
        for (int j = containsNullKey ? size - 1 : size; j-- != 0;) {
            while (((key[--i]) == null));
            if (!((newKey[pos = HashCommon.mix(h = hash[i]) & mask]) == null))
                while (!((newKey[pos = (pos + 1) & mask]) == null));
            newKey[pos] = key[i];
            newValue[pos] = value[i];
            newHash[pos] = h;
        }
        newValue[newN] = value[n];
        n = newN;
        this.mask = mask;
        maxFill = maxFill(n, f);
        this.key = newKey;
        this.value = newValue;
        this.hash = newHash;
    }

    @Override
    public Object2ObjectOpenCachedHashMap<K, V> clone() {
        Object2ObjectOpenCachedHashMap<K, V> c = (Object2ObjectOpenCachedHashMap<K, V>) super.clone();
        c.hash = hash.clone();
        return c;
    }

    @Override
    public int hashCode() {
        int h = 0;
        final K[] key = this.key;
        final V[] value = this.value;
        final int[] hash = this.hash;
        for (int j = containsNullKey ? size - 1 : size, i = 0, t = 0; j-- != 0;) {
            while ((key[i]) == null) i++;
            if (this != key[i]) t = hash[i];
            if (this != value[i]) t ^= (value[i] == null ? 0 : value[i].hashCode());
            h += t;
            i++;
        }
        if (containsNullKey) h += (value[n] == null ? 0 : value[n].hashCode());
        return h;
    }
}
