package com.modularmc.mmclutils.api.collection.map.enums;

import it.unimi.dsi.fastutil.ints.AbstractIntCollection;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.objects.*;

import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntBinaryOperator;
import java.util.function.ToIntFunction;

/** 用枚举序号直接寻址的映射。槽位的零值表示缺少该键；读取缺失键时返回配置的默认值。 迭代器可复用同一个可变条目。 */
public final class Enum2IntMap<K extends Enum<K>> extends AbstractReference2IntMap<K> {

    private final Class<K> keyType;
    private final K[] keys;
    private final int[] vals;
    private int size;

    private final EntrySet entrySet = new EntrySet();
    private final ReferenceSet<K> keySet = new KeySet();
    private final IntCollection values = new Values();

    /** 根据参数构造容器。 */
    public Enum2IntMap(Class<K> keyType) {
        this.keyType = keyType;
        this.keys = EnumMaps.universe(keyType);
        this.vals = new int[keys.length];
    }

    /** 复制映射的数据和配置。 */
    public Enum2IntMap(Enum2IntMap<K> map) {
        this.keyType = map.keyType;
        this.keys = map.keys;
        this.vals = map.vals.clone();
        this.size = map.size;
    }

    /** 返回此映射按序号直接寻址时使用的枚举类型。 */
    public Class<K> keyType() {
        return keyType;
    }

    @Override
    public int getInt(Object key) {
        if (!isValidKey(key)) return defRetValue;
        int v = vals[((Enum<?>) key).ordinal()];
        return v != 0 ? v : defRetValue;
    }

    @Override
    public int getOrDefault(Object key, int defaultValue) {
        if (!isValidKey(key)) return defaultValue;
        int v = vals[((Enum<?>) key).ordinal()];
        return v != 0 ? v : defaultValue;
    }

    /** 写入零值会移除映射，缺失时返回默认返回值。 */
    @Override
    public int put(K key, int value) {
        int i = key.ordinal();
        int old = vals[i];
        vals[i] = value;
        if (value == 0) {
            if (old != 0) size--;
        } else if (old == 0) {
            size++;
        }
        return old != 0 ? old : defRetValue;
    }

    @Override
    public int removeInt(Object key) {
        if (!isValidKey(key)) return defRetValue;
        int i = ((Enum<?>) key).ordinal();
        int old = vals[i];
        if (old == 0) return defRetValue;
        vals[i] = 0;
        size--;
        return old;
    }

    @Override
    public boolean containsKey(Object key) {
        return isValidKey(key) && vals[((Enum<?>) key).ordinal()] != 0;
    }

    @Override
    public boolean containsValue(int value) {
        if (value == 0) return false;
        final var vals = Enum2IntMap.this.vals;
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
        Arrays.fill(vals, 0);
        size = 0;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super Integer> action) {
        final var vals = Enum2IntMap.this.vals;
        final int length = vals.length;
        for (int i = 0; i < length; i++) {
            if (vals[i] != 0) action.accept(keys[i], Integer.valueOf(vals[i]));
        }
    }

    @Override
    public FastEntrySet<K> reference2IntEntrySet() {
        return entrySet;
    }

    @Override
    public ReferenceSet<K> keySet() {
        return keySet;
    }

    @Override
    public IntCollection values() {
        return values;
    }

    @Override
    public int putIfAbsent(K key, int value) {
        int i = key.ordinal();
        int old = vals[i];
        if (old != 0) return old;
        if (value != 0) {
            vals[i] = value;
            size++;
        }
        return defRetValue;
    }

    @Override
    public boolean remove(Object key, int value) {
        if (value == 0 || !isValidKey(key)) return false;
        int i = ((Enum<?>) key).ordinal();
        if (vals[i] != value) return false;
        vals[i] = 0;
        size--;
        return true;
    }

    @Override
    public boolean replace(K key, int oldValue, int newValue) {
        if (oldValue == 0 || vals[key.ordinal()] != oldValue) return false;
        put(key, newValue);
        return true;
    }

    @Override
    public int replace(K key, int value) {
        int old = vals[key.ordinal()];
        if (old == 0) return defRetValue;
        put(key, value);
        return old;
    }

    @Override
    public int computeIfAbsent(K key, ToIntFunction<? super K> mappingFunction) {
        int i = key.ordinal();
        int v = vals[i];
        if (v != 0) return v;
        int newValue = mappingFunction.applyAsInt(key);
        if (newValue != 0) {
            vals[i] = newValue;
            size++;
        }
        return newValue;
    }

    @Override
    public int computeIfAbsent(K key, Reference2IntFunction<? super K> mappingFunction) {
        int i = key.ordinal();
        int v = vals[i];
        if (v != 0) return v;
        int newValue = mappingFunction.getInt(key);
        if (newValue != 0) {
            vals[i] = newValue;
            size++;
        }
        return newValue;
    }

    @Override
    public int computeIntIfPresent(K key, BiFunction<? super K, ? super Integer, ? extends Integer> remappingFunction) {
        int i = key.ordinal();
        int old = vals[i];
        if (old == 0) return defRetValue;
        Integer newValue = remappingFunction.apply(key, Integer.valueOf(old));
        if (newValue == null) {
            vals[i] = 0;
            size--;
            return defRetValue;
        }
        put(key, newValue);
        return newValue;
    }

    @Override
    public int computeInt(K key, BiFunction<? super K, ? super Integer, ? extends Integer> remappingFunction) {
        int i = key.ordinal();
        int old = vals[i];
        Integer newValue = remappingFunction.apply(key, old == 0 ? null : Integer.valueOf(old));
        if (newValue == null) {
            if (old != 0) {
                vals[i] = 0;
                size--;
            }
            return defRetValue;
        }
        put(key, newValue);
        return newValue;
    }

    public int addTo(K key, int incr) {
        int i = key.ordinal();
        int old = vals[i];
        if (old == 0) {

            int sum = defRetValue + incr;
            if (sum == 0) return defRetValue;
            vals[i] = sum;
            size++;
            return defRetValue;
        }
        int sum = old + incr;
        vals[i] = sum;
        if (sum == 0) size--;
        return old;
    }

    @Override
    public int mergeInt(K key, int value, IntBinaryOperator remappingFunction) {
        int i = key.ordinal();
        int old = vals[i];
        int newValue = old == 0 ? value : remappingFunction.applyAsInt(old, value);
        if (newValue == 0) {
            if (old != 0) {
                vals[i] = 0;
                size--;
            }
            return defRetValue;
        }
        if (old == 0) size++;
        vals[i] = newValue;
        return newValue;
    }

    /** 同类型容器可直接沿值数组遍历。 */
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Map<?, ?> m)) return false;
        if (m.size() != size) return false;
        if (o instanceof Enum2IntMap<?> other && other.keyType == keyType) {
            return Arrays.equals(vals, other.vals);
        }
        final var vals = this.vals;
        final var keys = this.keys;
        for (int i = 0, n = vals.length; i < n; i++) {
            int v = vals[i];
            if (v != 0) {
                Object ov = m.get(keys[i]);
                if (!(ov instanceof Integer iv) || iv.intValue() != v) return false;
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
            int v = vals[i];
            if (v != 0) h += keys[i].hashCode() ^ v;
        }
        return h;
    }

    /** 同类型容器可直接沿值数组遍历。 */
    @Override
    public void putAll(Map<? extends K, ? extends Integer> m) {
        if (m instanceof Enum2IntMap<?> other && other.keyType == keyType) {
            if (other.size == 0) return;
            final int[] src = other.vals;
            final int[] dst = this.vals;
            int sz = this.size;
            for (int i = 0, n = dst.length; i < n; i++) {
                int v = src[i];
                if (v != 0) {
                    if (dst[i] == 0) sz++;
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

    private final class EntrySet extends AbstractObjectSet<Reference2IntMap.Entry<K>>
                                 implements Reference2IntMap.FastEntrySet<K> {

        @Override
        public ObjectIterator<Reference2IntMap.Entry<K>> iterator() {
            return new EntryIterator();
        }

        @Override
        public ObjectIterator<Reference2IntMap.Entry<K>> fastIterator() {
            return new EntryIterator();
        }

        /** 迭代复用当前条目，下一次移动时内容会更新。 */
        @Override
        public void fastForEach(Consumer<? super Reference2IntMap.Entry<K>> consumer) {
            Entry entry = new Entry();
            final var vals = Enum2IntMap.this.vals;
            final int length = vals.length;
            for (int i = 0; i < length; i++) {
                if (vals[i] != 0) {
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
            Enum2IntMap.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            if (!isValidKey(e.getKey())) return false;
            int i = ((Enum<?>) e.getKey()).ordinal();
            Object ov = e.getValue();
            return vals[i] != 0 && ov instanceof Integer iv && iv.intValue() == vals[i];
        }

        @Override
        public boolean remove(Object o) {
            if (!contains(o)) return false;
            removeInt(((Map.Entry<?, ?>) o).getKey());
            return true;
        }
    }

    private final class Entry implements Reference2IntMap.Entry<K> {

        private int index = -1;

        @Override
        public K getKey() {
            return keys[index];
        }

        @Override
        public int getIntValue() {
            return vals[index];
        }

        @Override
        public int setValue(int value) {
            int old = vals[index];
            vals[index] = value;
            if (value == 0) {
                if (old != 0) size--;
            } else if (old == 0) {
                size++;
            }
            return old;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            Object ov = e.getValue();
            return e.getKey() == keys[index] && ov instanceof Integer iv && iv.intValue() == vals[index];
        }

        @Override
        public int hashCode() {
            return keys[index].hashCode() ^ vals[index];
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
            final var vals = Enum2IntMap.this.vals;
            final int length = vals.length;
            while (cursor < length && vals[cursor] == 0) {
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
            if (vals[current] != 0) {
                vals[current] = 0;
                size--;
            }
            current = -1;
        }
    }

    private final class EntryIterator extends IndexIterator implements ObjectIterator<Reference2IntMap.Entry<K>> {

        private final Entry entry = new Entry();

        @Override
        public Reference2IntMap.Entry<K> next() {
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

    private final class ValueIterator extends IndexIterator implements IntIterator {

        @Override
        public int nextInt() {
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
            Enum2IntMap.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            return containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            if (!containsKey(o)) return false;
            Enum2IntMap.this.removeInt(o);
            return true;
        }
    }

    private final class Values extends AbstractIntCollection {

        @Override
        public IntIterator iterator() {
            return new ValueIterator();
        }

        @Override
        public boolean contains(int value) {
            return Enum2IntMap.this.containsValue(value);
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void clear() {
            Enum2IntMap.this.clear();
        }
    }
}
