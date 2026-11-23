package com.modularmc.mmclutils.collection.map.enums;

import it.unimi.dsi.fastutil.longs.AbstractLongCollection;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.*;

import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongBinaryOperator;
import java.util.function.ToLongFunction;

/** 用枚举序号直接寻址的映射。槽位的零值表示缺少该键；读取缺失键时返回配置的默认值。 迭代器可复用同一个可变条目。 */
public final class Enum2LongMap<K extends Enum<K>> extends AbstractReference2LongMap<K> {

    private final Class<K> keyType;
    private final K[] keys;
    private final long[] vals;
    private int size;

    private final EntrySet entrySet = new EntrySet();
    private final ReferenceSet<K> keySet = new KeySet();
    private final LongCollection values = new Values();

    /** 根据参数构造容器。 */
    public Enum2LongMap(Class<K> keyType) {
        this.keyType = keyType;
        this.keys = EnumMaps.universe(keyType);
        this.vals = new long[keys.length];
    }

    /** 复制映射的数据和配置。 */
    public Enum2LongMap(Enum2LongMap<K> map) {
        this.keyType = map.keyType;
        this.keys = map.keys;
        this.vals = map.vals.clone();
        this.size = map.size;
    }

    public Class<K> keyType() {
        return keyType;
    }

    @Override
    public long getLong(Object key) {
        if (!isValidKey(key)) return defRetValue;
        long v = vals[((Enum<?>) key).ordinal()];
        return v != 0L ? v : defRetValue;
    }

    @Override
    public long getOrDefault(Object key, long defaultValue) {
        if (!isValidKey(key)) return defaultValue;
        long v = vals[((Enum<?>) key).ordinal()];
        return v != 0L ? v : defaultValue;
    }

    /** 写入零值会移除映射，缺失时返回默认返回值。 */
    @Override
    public long put(K key, long value) {
        int i = key.ordinal();
        long old = vals[i];
        vals[i] = value;
        if (value == 0L) {
            if (old != 0L) size--;
        } else if (old == 0L) {
            size++;
        }
        return old != 0L ? old : defRetValue;
    }

    @Override
    public long removeLong(Object key) {
        if (!isValidKey(key)) return defRetValue;
        int i = ((Enum<?>) key).ordinal();
        long old = vals[i];
        if (old == 0L) return defRetValue;
        vals[i] = 0L;
        size--;
        return old;
    }

    @Override
    public boolean containsKey(Object key) {
        return isValidKey(key) && vals[((Enum<?>) key).ordinal()] != 0L;
    }

    @Override
    public boolean containsValue(long value) {
        if (value == 0L) return false;
        final var vals = Enum2LongMap.this.vals;
        final int length = vals.length;
        for (int i = 0; i < length; i++) {
            if (vals[i] == value) return true;
        }
        return false;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void clear() {
        if (size == 0) return;
        Arrays.fill(vals, 0L);
        size = 0;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super Long> action) {
        final var vals = Enum2LongMap.this.vals;
        final int length = vals.length;
        for (int i = 0; i < length; i++) {
            if (vals[i] != 0L) action.accept(keys[i], Long.valueOf(vals[i]));
        }
    }

    @Override
    public FastEntrySet<K> reference2LongEntrySet() {
        return entrySet;
    }

    @Override
    public ReferenceSet<K> keySet() {
        return keySet;
    }

    @Override
    public LongCollection values() {
        return values;
    }

    @Override
    public long putIfAbsent(K key, long value) {
        int i = key.ordinal();
        long old = vals[i];
        if (old != 0L) return old;
        if (value != 0L) {
            vals[i] = value;
            size++;
        }
        return defRetValue;
    }

    @Override
    public boolean remove(Object key, long value) {
        if (value == 0L || !isValidKey(key)) return false;
        int i = ((Enum<?>) key).ordinal();
        if (vals[i] != value) return false;
        vals[i] = 0L;
        size--;
        return true;
    }

    @Override
    public boolean replace(K key, long oldValue, long newValue) {
        if (oldValue == 0L || vals[key.ordinal()] != oldValue) return false;
        put(key, newValue);
        return true;
    }

    @Override
    public long replace(K key, long value) {
        long old = vals[key.ordinal()];
        if (old == 0L) return defRetValue;
        put(key, value);
        return old;
    }

    @Override
    public long computeIfAbsent(K key, ToLongFunction<? super K> mappingFunction) {
        int i = key.ordinal();
        long v = vals[i];
        if (v != 0L) return v;
        long newValue = mappingFunction.applyAsLong(key);
        if (newValue != 0L) {
            vals[i] = newValue;
            size++;
        }
        return newValue;
    }

    @Override
    public long computeIfAbsent(K key, Reference2LongFunction<? super K> mappingFunction) {
        int i = key.ordinal();
        long v = vals[i];
        if (v != 0L) return v;
        long newValue = mappingFunction.getLong(key);
        if (newValue != 0L) {
            vals[i] = newValue;
            size++;
        }
        return newValue;
    }

    @Override
    public long computeLongIfPresent(K key, BiFunction<? super K, ? super Long, ? extends Long> remappingFunction) {
        int i = key.ordinal();
        long old = vals[i];
        if (old == 0L) return defRetValue;
        Long newValue = remappingFunction.apply(key, Long.valueOf(old));
        if (newValue == null) {
            vals[i] = 0L;
            size--;
            return defRetValue;
        }
        put(key, newValue);
        return newValue;
    }

    @Override
    public long computeLong(K key, BiFunction<? super K, ? super Long, ? extends Long> remappingFunction) {
        int i = key.ordinal();
        long old = vals[i];
        Long newValue = remappingFunction.apply(key, old == 0L ? null : Long.valueOf(old));
        if (newValue == null) {
            if (old != 0L) {
                vals[i] = 0L;
                size--;
            }
            return defRetValue;
        }
        put(key, newValue);
        return newValue;
    }

    public long addTo(K key, long incr) {
        int i = key.ordinal();
        long old = vals[i];
        if (old == 0L) {

            long sum = defRetValue + incr;
            if (sum == 0L) return defRetValue;
            vals[i] = sum;
            size++;
            return defRetValue;
        }
        long sum = old + incr;
        vals[i] = sum;
        if (sum == 0L) size--;
        return old;
    }

    @Override
    public long mergeLong(K key, long value, LongBinaryOperator remappingFunction) {
        int i = key.ordinal();
        long old = vals[i];
        long newValue = old == 0L ? value : remappingFunction.applyAsLong(old, value);
        if (newValue == 0L) {
            if (old != 0L) {
                vals[i] = 0L;
                size--;
            }
            return defRetValue;
        }
        if (old == 0L) size++;
        vals[i] = newValue;
        return newValue;
    }

    /** 同类型容器可直接沿值数组遍历。 */
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Map<?, ?> m)) return false;
        if (m.size() != size) return false;
        if (o instanceof Enum2LongMap<?> other && other.keyType == keyType) {
            return Arrays.equals(vals, other.vals);
        }
        final var vals = this.vals;
        final var keys = this.keys;
        for (int i = 0, n = vals.length; i < n; i++) {
            long v = vals[i];
            if (v != 0L) {
                Object ov = m.get(keys[i]);
                if (!(ov instanceof Long lv) || lv.longValue() != v) return false;
            }
        }
        return true;
    }

    /** 迭代复用当前条目，下一次移动时内容会更新。 */
    @Override
    public int hashCode() {
        int h = 0;
        final var vals = this.vals;
        final var keys = this.keys;
        for (int i = 0, n = vals.length; i < n; i++) {
            long v = vals[i];
            if (v != 0L) h += keys[i].hashCode() ^ Long.hashCode(v);
        }
        return h;
    }

    /** 同类型容器可直接沿值数组遍历。 */
    @Override
    public void putAll(Map<? extends K, ? extends Long> m) {
        if (m instanceof Enum2LongMap<?> other && other.keyType == keyType) {
            if (other.size == 0) return;
            final long[] src = other.vals;
            final long[] dst = this.vals;
            int sz = this.size;
            for (int i = 0, n = dst.length; i < n; i++) {
                long v = src[i];
                if (v != 0L) {
                    if (dst[i] == 0L) sz++;
                    dst[i] = v;
                }
            }
            this.size = sz;
            return;
        }
        super.putAll(m);
    }

    private boolean isValidKey(Object key) {
        if (key == null) return false;
        Class<?> keyClass = key.getClass();
        return keyClass == keyType || keyClass.getSuperclass() == keyType;
    }

    private final class EntrySet extends AbstractObjectSet<Reference2LongMap.Entry<K>>
                                 implements Reference2LongMap.FastEntrySet<K> {

        @Override
        public ObjectIterator<Reference2LongMap.Entry<K>> iterator() {
            return new EntryIterator();
        }

        @Override
        public ObjectIterator<Reference2LongMap.Entry<K>> fastIterator() {
            return new EntryIterator();
        }

        /** 迭代复用当前条目，下一次移动时内容会更新。 */
        @Override
        public void fastForEach(Consumer<? super Reference2LongMap.Entry<K>> consumer) {
            Entry entry = new Entry();
            final var vals = Enum2LongMap.this.vals;
            final int length = vals.length;
            for (int i = 0; i < length; i++) {
                if (vals[i] != 0L) {
                    entry.index = i;
                    consumer.accept(entry);
                }
            }
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void clear() {
            Enum2LongMap.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            if (!isValidKey(e.getKey())) return false;
            int i = ((Enum<?>) e.getKey()).ordinal();
            Object ov = e.getValue();
            return vals[i] != 0L && ov instanceof Long lv && lv.longValue() == vals[i];
        }

        @Override
        public boolean remove(Object o) {
            if (!contains(o)) return false;
            removeLong(((Map.Entry<?, ?>) o).getKey());
            return true;
        }
    }

    private final class Entry implements Reference2LongMap.Entry<K> {

        private int index = -1;

        @Override
        public K getKey() {
            return keys[index];
        }

        @Override
        public long getLongValue() {
            return vals[index];
        }

        @Override
        public long setValue(long value) {
            long old = vals[index];
            vals[index] = value;
            if (value == 0L) {
                if (old != 0L) size--;
            } else if (old == 0L) {
                size++;
            }
            return old;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            Object ov = e.getValue();
            return e.getKey() == keys[index] && ov instanceof Long lv && lv.longValue() == vals[index];
        }

        @Override
        public int hashCode() {
            return keys[index].hashCode() ^ Long.hashCode(vals[index]);
        }

        @Override
        public String toString() {
            return keys[index] + "=" + vals[index];
        }
    }

    private abstract class IndexIterator {

        int cursor;
        int current = -1;

        public boolean hasNext() {
            final var vals = Enum2LongMap.this.vals;
            final int length = vals.length;
            while (cursor < length && vals[cursor] == 0L) {
                cursor++;
            }
            return cursor < length;
        }

        int advance() {
            if (!hasNext()) throw new NoSuchElementException();
            return current = cursor++;
        }

        public void remove() {
            if (current < 0) throw new IllegalStateException();
            if (vals[current] != 0L) {
                vals[current] = 0L;
                size--;
            }
            current = -1;
        }
    }

    private final class EntryIterator extends IndexIterator implements ObjectIterator<Reference2LongMap.Entry<K>> {

        private final Entry entry = new Entry();

        @Override
        public Reference2LongMap.Entry<K> next() {
            entry.index = advance();
            return entry;
        }
    }

    private final class KeyIterator extends IndexIterator implements ObjectIterator<K> {

        @Override
        public K next() {
            return keys[advance()];
        }
    }

    private final class ValueIterator extends IndexIterator implements LongIterator {

        @Override
        public long nextLong() {
            return vals[advance()];
        }
    }

    private final class KeySet extends AbstractReferenceSet<K> {

        @Override
        public ObjectIterator<K> iterator() {
            return new KeyIterator();
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void clear() {
            Enum2LongMap.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            return containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            if (!containsKey(o)) return false;
            Enum2LongMap.this.removeLong(o);
            return true;
        }
    }

    private final class Values extends AbstractLongCollection {

        @Override
        public LongIterator iterator() {
            return new ValueIterator();
        }

        @Override
        public boolean contains(long value) {
            return Enum2LongMap.this.containsValue(value);
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void clear() {
            Enum2LongMap.this.clear();
        }
    }
}
