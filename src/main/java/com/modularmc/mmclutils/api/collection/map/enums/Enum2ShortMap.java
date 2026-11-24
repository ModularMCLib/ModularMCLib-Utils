package com.modularmc.mmclutils.api.collection.map.enums;

import it.unimi.dsi.fastutil.objects.*;
import it.unimi.dsi.fastutil.shorts.AbstractShortCollection;
import it.unimi.dsi.fastutil.shorts.ShortBinaryOperator;
import it.unimi.dsi.fastutil.shorts.ShortCollection;
import it.unimi.dsi.fastutil.shorts.ShortIterator;

import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/** 用枚举序号直接寻址的映射。槽位的零值表示缺少该键；读取缺失键时返回配置的默认值。 迭代器可复用同一个可变条目。 */
public final class Enum2ShortMap<K extends Enum<K>> extends AbstractReference2ShortMap<K> {

    private final Class<K> keyType;
    private final K[] keys;
    private final short[] vals;
    private int size;

    private final EntrySet entrySet = new EntrySet();
    private final ReferenceSet<K> keySet = new KeySet();
    private final ShortCollection values = new Values();

    /** 根据参数构造容器。 */
    public Enum2ShortMap(Class<K> keyType) {
        this.keyType = keyType;
        this.keys = EnumMaps.universe(keyType);
        this.vals = new short[keys.length];
    }

    /** 复制映射的数据和配置。 */
    public Enum2ShortMap(Enum2ShortMap<K> map) {
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
    public short getShort(Object key) {
        if (!isValidKey(key)) return defRetValue;
        short v = vals[((Enum<?>) key).ordinal()];
        return v != (short) 0 ? v : defRetValue;
    }

    @Override
    public short getOrDefault(Object key, short defaultValue) {
        if (!isValidKey(key)) return defaultValue;
        short v = vals[((Enum<?>) key).ordinal()];
        return v != (short) 0 ? v : defaultValue;
    }

    /** 写入零值会移除映射，缺失时返回默认返回值。 */
    @Override
    public short put(K key, short value) {
        int i = key.ordinal();
        short old = vals[i];
        vals[i] = value;
        if (value == (short) 0) {
            if (old != (short) 0) size--;
        } else if (old == (short) 0) {
            size++;
        }
        return old != (short) 0 ? old : defRetValue;
    }

    @Override
    public short removeShort(Object key) {
        if (!isValidKey(key)) return defRetValue;
        int i = ((Enum<?>) key).ordinal();
        short old = vals[i];
        if (old == (short) 0) return defRetValue;
        vals[i] = (short) 0;
        size--;
        return old;
    }

    @Override
    public boolean containsKey(Object key) {
        return isValidKey(key) && vals[((Enum<?>) key).ordinal()] != (short) 0;
    }

    @Override
    public boolean containsValue(short value) {
        if (value == (short) 0) return false;
        final var vals = Enum2ShortMap.this.vals;
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
        Arrays.fill(vals, (short) 0);
        size = 0;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super Short> action) {
        final var vals = Enum2ShortMap.this.vals;
        final int length = vals.length;
        for (int i = 0; i < length; i++) {
            if (vals[i] != (short) 0) action.accept(keys[i], Short.valueOf(vals[i]));
        }
    }

    @Override
    public FastEntrySet<K> reference2ShortEntrySet() {
        return entrySet;
    }

    @Override
    public ReferenceSet<K> keySet() {
        return keySet;
    }

    @Override
    public ShortCollection values() {
        return values;
    }

    @Override
    public short putIfAbsent(K key, short value) {
        int i = key.ordinal();
        short old = vals[i];
        if (old != (short) 0) return old;
        if (value != (short) 0) {
            vals[i] = value;
            size++;
        }
        return defRetValue;
    }

    @Override
    public boolean remove(Object key, short value) {
        if (value == (short) 0 || !isValidKey(key)) return false;
        int i = ((Enum<?>) key).ordinal();
        if (vals[i] != value) return false;
        vals[i] = (short) 0;
        size--;
        return true;
    }

    @Override
    public boolean replace(K key, short oldValue, short newValue) {
        if (oldValue == (short) 0 || vals[key.ordinal()] != oldValue) return false;
        put(key, newValue);
        return true;
    }

    @Override
    public short replace(K key, short value) {
        short old = vals[key.ordinal()];
        if (old == (short) 0) return defRetValue;
        put(key, value);
        return old;
    }

    @Override
    public short computeIfAbsent(K key, ToIntFunction<? super K> mappingFunction) {
        int i = key.ordinal();
        short v = vals[i];
        if (v != (short) 0) return v;
        short newValue = (short) mappingFunction.applyAsInt(key);
        if (newValue != (short) 0) {
            vals[i] = newValue;
            size++;
        }
        return newValue;
    }

    @Override
    public short computeIfAbsent(K key, Reference2ShortFunction<? super K> mappingFunction) {
        int i = key.ordinal();
        short v = vals[i];
        if (v != (short) 0) return v;
        short newValue = mappingFunction.getShort(key);
        if (newValue != (short) 0) {
            vals[i] = newValue;
            size++;
        }
        return newValue;
    }

    @Override
    public short computeShortIfPresent(K key, BiFunction<? super K, ? super Short, ? extends Short> remappingFunction) {
        int i = key.ordinal();
        short old = vals[i];
        if (old == (short) 0) return defRetValue;
        Short newValue = remappingFunction.apply(key, Short.valueOf(old));
        if (newValue == null) {
            vals[i] = (short) 0;
            size--;
            return defRetValue;
        }
        put(key, newValue);
        return newValue;
    }

    @Override
    public short computeShort(K key, BiFunction<? super K, ? super Short, ? extends Short> remappingFunction) {
        int i = key.ordinal();
        short old = vals[i];
        Short newValue = remappingFunction.apply(key, old == (short) 0 ? null : Short.valueOf(old));
        if (newValue == null) {
            if (old != (short) 0) {
                vals[i] = (short) 0;
                size--;
            }
            return defRetValue;
        }
        put(key, newValue);
        return newValue;
    }

    public short addTo(K key, short incr) {
        int i = key.ordinal();
        short old = vals[i];
        if (old == (short) 0) {

            short sum = (short) (defRetValue + incr);
            if (sum == (short) 0) return defRetValue;
            vals[i] = sum;
            size++;
            return defRetValue;
        }
        short sum = (short) (old + incr);
        vals[i] = sum;
        if (sum == (short) 0) size--;
        return old;
    }

    @Override
    public short mergeShort(K key, short value, ShortBinaryOperator remappingFunction) {
        int i = key.ordinal();
        short old = vals[i];
        short newValue = old == (short) 0 ? value : remappingFunction.apply(old, value);
        if (newValue == (short) 0) {
            if (old != (short) 0) {
                vals[i] = (short) 0;
                size--;
            }
            return defRetValue;
        }
        if (old == (short) 0) size++;
        vals[i] = newValue;
        return newValue;
    }

    /** 同类型容器可直接沿值数组遍历。 */
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Map<?, ?> m)) return false;
        if (m.size() != size) return false;
        if (o instanceof Enum2ShortMap<?> other && other.keyType == keyType) {
            return Arrays.equals(vals, other.vals);
        }
        final var vals = this.vals;
        final var keys = this.keys;
        for (int i = 0, n = vals.length; i < n; i++) {
            short v = vals[i];
            if (v != (short) 0) {
                Object ov = m.get(keys[i]);
                if (!(ov instanceof Short sv) || sv.shortValue() != v) return false;
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
            short v = vals[i];
            if (v != (short) 0) h += keys[i].hashCode() ^ v;
        }
        return h;
    }

    /** 同类型容器可直接沿值数组遍历。 */
    @Override
    public void putAll(Map<? extends K, ? extends Short> m) {
        if (m instanceof Enum2ShortMap<?> other && other.keyType == keyType) {
            if (other.size == 0) return;
            final short[] src = other.vals;
            final short[] dst = this.vals;
            int sz = this.size;
            for (int i = 0, n = dst.length; i < n; i++) {
                short v = src[i];
                if (v != (short) 0) {
                    if (dst[i] == (short) 0) sz++;
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

    private final class EntrySet extends AbstractObjectSet<Reference2ShortMap.Entry<K>>
                                 implements Reference2ShortMap.FastEntrySet<K> {

        @Override
        public ObjectIterator<Reference2ShortMap.Entry<K>> iterator() {
            return new EntryIterator();
        }

        @Override
        public ObjectIterator<Reference2ShortMap.Entry<K>> fastIterator() {
            return new EntryIterator();
        }

        /** 迭代复用当前条目，下一次移动时内容会更新。 */
        @Override
        public void fastForEach(Consumer<? super Reference2ShortMap.Entry<K>> consumer) {
            Entry entry = new Entry();
            final var vals = Enum2ShortMap.this.vals;
            final int length = vals.length;
            for (int i = 0; i < length; i++) {
                if (vals[i] != (short) 0) {
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
            Enum2ShortMap.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            if (!isValidKey(e.getKey())) return false;
            int i = ((Enum<?>) e.getKey()).ordinal();
            Object ov = e.getValue();
            return vals[i] != (short) 0 && ov instanceof Short sv && sv.shortValue() == vals[i];
        }

        @Override
        public boolean remove(Object o) {
            if (!contains(o)) return false;
            removeShort(((Map.Entry<?, ?>) o).getKey());
            return true;
        }
    }

    private final class Entry implements Reference2ShortMap.Entry<K> {

        private int index = -1;

        @Override
        public K getKey() {
            return keys[index];
        }

        @Override
        public short getShortValue() {
            return vals[index];
        }

        @Override
        public short setValue(short value) {
            short old = vals[index];
            vals[index] = value;
            if (value == (short) 0) {
                if (old != (short) 0) size--;
            } else if (old == (short) 0) {
                size++;
            }
            return old;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            Object ov = e.getValue();
            return e.getKey() == keys[index] && ov instanceof Short sv && sv.shortValue() == vals[index];
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
            final var vals = Enum2ShortMap.this.vals;
            final int length = vals.length;
            while (cursor < length && vals[cursor] == (short) 0) {
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
            if (vals[current] != (short) 0) {
                vals[current] = (short) 0;
                size--;
            }
            current = -1;
        }
    }

    private final class EntryIterator extends IndexIterator implements ObjectIterator<Reference2ShortMap.Entry<K>> {

        private final Entry entry = new Entry();

        @Override
        public Reference2ShortMap.Entry<K> next() {
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

    private final class ValueIterator extends IndexIterator implements ShortIterator {

        @Override
        public short nextShort() {
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
            Enum2ShortMap.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            return containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            if (!containsKey(o)) return false;
            Enum2ShortMap.this.removeShort(o);
            return true;
        }
    }

    private final class Values extends AbstractShortCollection {

        @Override
        public ShortIterator iterator() {
            return new ValueIterator();
        }

        @Override
        public boolean contains(short value) {
            return Enum2ShortMap.this.containsValue(value);
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void clear() {
            Enum2ShortMap.this.clear();
        }
    }
}
