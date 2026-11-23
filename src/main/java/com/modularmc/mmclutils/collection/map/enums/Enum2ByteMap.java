package com.modularmc.mmclutils.collection.map.enums;

import it.unimi.dsi.fastutil.bytes.AbstractByteCollection;
import it.unimi.dsi.fastutil.bytes.ByteBinaryOperator;
import it.unimi.dsi.fastutil.bytes.ByteCollection;
import it.unimi.dsi.fastutil.bytes.ByteIterator;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.AbstractReference2ByteMap;
import it.unimi.dsi.fastutil.objects.AbstractReferenceSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ByteFunction;
import it.unimi.dsi.fastutil.objects.Reference2ByteMap;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/** 用枚举序号直接寻址的映射。槽位的零值表示缺少该键；读取缺失键时返回配置的默认值。 迭代器可复用同一个可变条目。 */
public final class Enum2ByteMap<K extends Enum<K>> extends AbstractReference2ByteMap<K> {

    private final Class<K> keyType;
    private final K[] keys;
    private final byte[] vals;
    private int size;

    private final EntrySet entrySet = new EntrySet();
    private final ReferenceSet<K> keySet = new KeySet();
    private final ByteCollection values = new Values();

    /** 根据参数构造容器。 */
    public Enum2ByteMap(Class<K> keyType) {
        this.keyType = keyType;
        this.keys = EnumMaps.universe(keyType);
        this.vals = new byte[keys.length];
    }

    /** 复制映射的数据和配置。 */
    public Enum2ByteMap(Enum2ByteMap<K> map) {
        this.keyType = map.keyType;
        this.keys = map.keys;
        this.vals = map.vals.clone();
        this.size = map.size;
    }

    public Class<K> keyType() {
        return keyType;
    }

    @Override
    public byte getByte(Object key) {
        if (!isValidKey(key)) return defRetValue;
        byte v = vals[((Enum<?>) key).ordinal()];
        return v != (byte) 0 ? v : defRetValue;
    }

    @Override
    public byte getOrDefault(Object key, byte defaultValue) {
        if (!isValidKey(key)) return defaultValue;
        byte v = vals[((Enum<?>) key).ordinal()];
        return v != (byte) 0 ? v : defaultValue;
    }

    /** 写入零值会移除映射，缺失时返回默认返回值。 */
    @Override
    public byte put(K key, byte value) {
        int i = key.ordinal();
        byte old = vals[i];
        vals[i] = value;
        if (value == (byte) 0) {
            if (old != (byte) 0) size--;
        } else if (old == (byte) 0) {
            size++;
        }
        return old != (byte) 0 ? old : defRetValue;
    }

    @Override
    public byte removeByte(Object key) {
        if (!isValidKey(key)) return defRetValue;
        int i = ((Enum<?>) key).ordinal();
        byte old = vals[i];
        if (old == (byte) 0) return defRetValue;
        vals[i] = (byte) 0;
        size--;
        return old;
    }

    @Override
    public boolean containsKey(Object key) {
        return isValidKey(key) && vals[((Enum<?>) key).ordinal()] != (byte) 0;
    }

    @Override
    public boolean containsValue(byte value) {
        if (value == (byte) 0) return false;
        final var vals = Enum2ByteMap.this.vals;
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
        Arrays.fill(vals, (byte) 0);
        size = 0;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super Byte> action) {
        final var vals = Enum2ByteMap.this.vals;
        final int length = vals.length;
        for (int i = 0; i < length; i++) {
            if (vals[i] != (byte) 0) action.accept(keys[i], Byte.valueOf(vals[i]));
        }
    }

    @Override
    public FastEntrySet<K> reference2ByteEntrySet() {
        return entrySet;
    }

    @Override
    public ReferenceSet<K> keySet() {
        return keySet;
    }

    @Override
    public ByteCollection values() {
        return values;
    }

    @Override
    public byte putIfAbsent(K key, byte value) {
        int i = key.ordinal();
        byte old = vals[i];
        if (old != (byte) 0) return old;
        if (value != (byte) 0) {
            vals[i] = value;
            size++;
        }
        return defRetValue;
    }

    @Override
    public boolean remove(Object key, byte value) {
        if (value == (byte) 0 || !isValidKey(key)) return false;
        int i = ((Enum<?>) key).ordinal();
        if (vals[i] != value) return false;
        vals[i] = (byte) 0;
        size--;
        return true;
    }

    @Override
    public boolean replace(K key, byte oldValue, byte newValue) {
        if (oldValue == (byte) 0 || vals[key.ordinal()] != oldValue) return false;
        put(key, newValue);
        return true;
    }

    @Override
    public byte replace(K key, byte value) {
        byte old = vals[key.ordinal()];
        if (old == (byte) 0) return defRetValue;
        put(key, value);
        return old;
    }

    @Override
    public byte computeIfAbsent(K key, ToIntFunction<? super K> mappingFunction) {
        int i = key.ordinal();
        byte v = vals[i];
        if (v != (byte) 0) return v;
        byte newValue = (byte) mappingFunction.applyAsInt(key);
        if (newValue != (byte) 0) {
            vals[i] = newValue;
            size++;
        }
        return newValue;
    }

    @Override
    public byte computeIfAbsent(K key, Reference2ByteFunction<? super K> mappingFunction) {
        int i = key.ordinal();
        byte v = vals[i];
        if (v != (byte) 0) return v;
        byte newValue = mappingFunction.getByte(key);
        if (newValue != (byte) 0) {
            vals[i] = newValue;
            size++;
        }
        return newValue;
    }

    @Override
    public byte computeByteIfPresent(K key, BiFunction<? super K, ? super Byte, ? extends Byte> remappingFunction) {
        int i = key.ordinal();
        byte old = vals[i];
        if (old == (byte) 0) return defRetValue;
        Byte newValue = remappingFunction.apply(key, Byte.valueOf(old));
        if (newValue == null) {
            vals[i] = (byte) 0;
            size--;
            return defRetValue;
        }
        put(key, newValue);
        return newValue;
    }

    @Override
    public byte computeByte(K key, BiFunction<? super K, ? super Byte, ? extends Byte> remappingFunction) {
        int i = key.ordinal();
        byte old = vals[i];
        Byte newValue = remappingFunction.apply(key, old == (byte) 0 ? null : Byte.valueOf(old));
        if (newValue == null) {
            if (old != (byte) 0) {
                vals[i] = (byte) 0;
                size--;
            }
            return defRetValue;
        }
        put(key, newValue);
        return newValue;
    }

    public byte addTo(K key, byte incr) {
        int i = key.ordinal();
        byte old = vals[i];
        if (old == (byte) 0) {

            byte sum = (byte) (defRetValue + incr);
            if (sum == (byte) 0) return defRetValue;
            vals[i] = sum;
            size++;
            return defRetValue;
        }
        byte sum = (byte) (old + incr);
        vals[i] = sum;
        if (sum == (byte) 0) size--;
        return old;
    }

    @Override
    public byte mergeByte(K key, byte value, ByteBinaryOperator remappingFunction) {
        int i = key.ordinal();
        byte old = vals[i];
        byte newValue = old == (byte) 0 ? value : remappingFunction.apply(old, value);
        if (newValue == (byte) 0) {
            if (old != (byte) 0) {
                vals[i] = (byte) 0;
                size--;
            }
            return defRetValue;
        }
        if (old == (byte) 0) size++;
        vals[i] = newValue;
        return newValue;
    }

    /** 同类型容器可直接沿值数组遍历。 */
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Map<?, ?> m)) return false;
        if (m.size() != size) return false;
        if (o instanceof Enum2ByteMap<?> other && other.keyType == keyType) {
            return Arrays.equals(vals, other.vals);
        }
        final var vals = this.vals;
        final var keys = this.keys;
        for (int i = 0, n = vals.length; i < n; i++) {
            byte v = vals[i];
            if (v != (byte) 0) {
                Object ov = m.get(keys[i]);
                if (!(ov instanceof Byte bv) || bv.byteValue() != v) return false;
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
            byte v = vals[i];
            if (v != (byte) 0) h += keys[i].hashCode() ^ v;
        }
        return h;
    }

    /** 同类型容器可直接沿值数组遍历。 */
    @Override
    public void putAll(Map<? extends K, ? extends Byte> m) {
        if (m instanceof Enum2ByteMap<?> other && other.keyType == keyType) {
            if (other.size == 0) return;
            final byte[] src = other.vals;
            final byte[] dst = this.vals;
            int sz = this.size;
            for (int i = 0, n = dst.length; i < n; i++) {
                byte v = src[i];
                if (v != (byte) 0) {
                    if (dst[i] == (byte) 0) sz++;
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

    private final class EntrySet extends AbstractObjectSet<Reference2ByteMap.Entry<K>>
                                 implements Reference2ByteMap.FastEntrySet<K> {

        @Override
        public ObjectIterator<Reference2ByteMap.Entry<K>> iterator() {
            return new EntryIterator();
        }

        @Override
        public ObjectIterator<Reference2ByteMap.Entry<K>> fastIterator() {
            return new EntryIterator();
        }

        /** 迭代复用当前条目，下一次移动时内容会更新。 */
        @Override
        public void fastForEach(Consumer<? super Reference2ByteMap.Entry<K>> consumer) {
            Entry entry = new Entry();
            final var vals = Enum2ByteMap.this.vals;
            final int length = vals.length;
            for (int i = 0; i < length; i++) {
                if (vals[i] != (byte) 0) {
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
            Enum2ByteMap.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            if (!isValidKey(e.getKey())) return false;
            int i = ((Enum<?>) e.getKey()).ordinal();
            Object ov = e.getValue();
            return vals[i] != (byte) 0 && ov instanceof Byte bv && bv.byteValue() == vals[i];
        }

        @Override
        public boolean remove(Object o) {
            if (!contains(o)) return false;
            removeByte(((Map.Entry<?, ?>) o).getKey());
            return true;
        }
    }

    private final class Entry implements Reference2ByteMap.Entry<K> {

        private int index = -1;

        @Override
        public K getKey() {
            return keys[index];
        }

        @Override
        public byte getByteValue() {
            return vals[index];
        }

        @Override
        public byte setValue(byte value) {
            byte old = vals[index];
            vals[index] = value;
            if (value == (byte) 0) {
                if (old != (byte) 0) size--;
            } else if (old == (byte) 0) {
                size++;
            }
            return old;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            Object ov = e.getValue();
            return e.getKey() == keys[index] && ov instanceof Byte bv && bv.byteValue() == vals[index];
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
            final var vals = Enum2ByteMap.this.vals;
            final int length = vals.length;
            while (cursor < length && vals[cursor] == (byte) 0) {
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
            if (vals[current] != (byte) 0) {
                vals[current] = (byte) 0;
                size--;
            }
            current = -1;
        }
    }

    private final class EntryIterator extends IndexIterator implements ObjectIterator<Reference2ByteMap.Entry<K>> {

        private final Entry entry = new Entry();

        @Override
        public Reference2ByteMap.Entry<K> next() {
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

    private final class ValueIterator extends IndexIterator implements ByteIterator {

        @Override
        public byte nextByte() {
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
            Enum2ByteMap.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            return containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            if (!containsKey(o)) return false;
            Enum2ByteMap.this.removeByte(o);
            return true;
        }
    }

    private final class Values extends AbstractByteCollection {

        @Override
        public ByteIterator iterator() {
            return new ValueIterator();
        }

        @Override
        public boolean contains(byte value) {
            return Enum2ByteMap.this.containsValue(value);
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void clear() {
            Enum2ByteMap.this.clear();
        }
    }
}
