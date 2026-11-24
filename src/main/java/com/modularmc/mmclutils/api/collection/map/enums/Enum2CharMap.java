package com.modularmc.mmclutils.api.collection.map.enums;

import it.unimi.dsi.fastutil.chars.AbstractCharCollection;
import it.unimi.dsi.fastutil.chars.CharBinaryOperator;
import it.unimi.dsi.fastutil.chars.CharCollection;
import it.unimi.dsi.fastutil.chars.CharIterator;
import it.unimi.dsi.fastutil.objects.*;

import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/** 用枚举序号直接寻址的映射。槽位的零值表示缺少该键；读取缺失键时返回配置的默认值。 迭代器可复用同一个可变条目。 */
public final class Enum2CharMap<K extends Enum<K>> extends AbstractReference2CharMap<K> {

    private final Class<K> keyType;
    private final K[] keys;
    private final char[] vals;
    private int size;

    private final EntrySet entrySet = new EntrySet();
    private final ReferenceSet<K> keySet = new KeySet();
    private final CharCollection values = new Values();

    /** 为该枚举的全部常量分配槽位。 */
    public Enum2CharMap(Class<K> keyType) {
        this.keyType = keyType;
        this.keys = EnumMaps.universe(keyType);
        this.vals = new char[keys.length];
    }

    /** 复制槽位中的条目，使用新映射的初始默认返回值。 */
    public Enum2CharMap(Enum2CharMap<K> map) {
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
    public char getChar(Object key) {
        if (!isValidKey(key)) return defRetValue;
        char v = vals[((Enum<?>) key).ordinal()];
        return v != (char) 0 ? v : defRetValue;
    }

    @Override
    public char getOrDefault(Object key, char defaultValue) {
        if (!isValidKey(key)) return defaultValue;
        char v = vals[((Enum<?>) key).ordinal()];
        return v != (char) 0 ? v : defaultValue;
    }

    /** 写入零值会移除映射，缺失时返回默认返回值。 */
    @Override
    public char put(K key, char value) {
        int i = key.ordinal();
        char old = vals[i];
        vals[i] = value;
        if (value == (char) 0) {
            if (old != (char) 0) size--;
        } else if (old == (char) 0) {
            size++;
        }
        return old != (char) 0 ? old : defRetValue;
    }

    @Override
    public char removeChar(Object key) {
        if (!isValidKey(key)) return defRetValue;
        int i = ((Enum<?>) key).ordinal();
        char old = vals[i];
        if (old == (char) 0) return defRetValue;
        vals[i] = (char) 0;
        size--;
        return old;
    }

    @Override
    public boolean containsKey(Object key) {
        return isValidKey(key) && vals[((Enum<?>) key).ordinal()] != (char) 0;
    }

    @Override
    public boolean containsValue(char value) {
        if (value == (char) 0) return false;
        final var vals = Enum2CharMap.this.vals;
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
        Arrays.fill(vals, (char) 0);
        size = 0;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super Character> action) {
        final var vals = Enum2CharMap.this.vals;
        final int length = vals.length;
        for (int i = 0; i < length; i++) {
            if (vals[i] != (char) 0) action.accept(keys[i], Character.valueOf(vals[i]));
        }
    }

    @Override
    public FastEntrySet<K> reference2CharEntrySet() {
        return entrySet;
    }

    @Override
    public ReferenceSet<K> keySet() {
        return keySet;
    }

    @Override
    public CharCollection values() {
        return values;
    }

    @Override
    public char putIfAbsent(K key, char value) {
        int i = key.ordinal();
        char old = vals[i];
        if (old != (char) 0) return old;
        if (value != (char) 0) {
            vals[i] = value;
            size++;
        }
        return defRetValue;
    }

    @Override
    public boolean remove(Object key, char value) {
        if (value == (char) 0 || !isValidKey(key)) return false;
        int i = ((Enum<?>) key).ordinal();
        if (vals[i] != value) return false;
        vals[i] = (char) 0;
        size--;
        return true;
    }

    @Override
    public boolean replace(K key, char oldValue, char newValue) {
        if (oldValue == (char) 0 || vals[key.ordinal()] != oldValue) return false;
        put(key, newValue);
        return true;
    }

    @Override
    public char replace(K key, char value) {
        char old = vals[key.ordinal()];
        if (old == (char) 0) return defRetValue;
        put(key, value);
        return old;
    }

    @Override
    public char computeIfAbsent(K key, ToIntFunction<? super K> mappingFunction) {
        int i = key.ordinal();
        char v = vals[i];
        if (v != (char) 0) return v;
        char newValue = (char) mappingFunction.applyAsInt(key);
        if (newValue != (char) 0) {
            vals[i] = newValue;
            size++;
        }
        return newValue;
    }

    @Override
    public char computeIfAbsent(K key, Reference2CharFunction<? super K> mappingFunction) {
        int i = key.ordinal();
        char v = vals[i];
        if (v != (char) 0) return v;
        char newValue = mappingFunction.getChar(key);
        if (newValue != (char) 0) {
            vals[i] = newValue;
            size++;
        }
        return newValue;
    }

    @Override
    public char computeCharIfPresent(K key, BiFunction<? super K, ? super Character, ? extends Character> remappingFunction) {
        int i = key.ordinal();
        char old = vals[i];
        if (old == (char) 0) return defRetValue;
        Character newValue = remappingFunction.apply(key, Character.valueOf(old));
        if (newValue == null) {
            vals[i] = (char) 0;
            size--;
            return defRetValue;
        }
        put(key, newValue);
        return newValue;
    }

    @Override
    public char computeChar(K key, BiFunction<? super K, ? super Character, ? extends Character> remappingFunction) {
        int i = key.ordinal();
        char old = vals[i];
        Character newValue = remappingFunction.apply(key, old == (char) 0 ? null : Character.valueOf(old));
        if (newValue == null) {
            if (old != (char) 0) {
                vals[i] = (char) 0;
                size--;
            }
            return defRetValue;
        }
        put(key, newValue);
        return newValue;
    }

    /**
     * 将增量加到现有值上并返回旧值。缺失项从默认返回值开始累加，结果为零时清除该项。
     *
     * @param key  要更新的枚举常量
     * @param incr 增量，允许为负
     * @return 累加前的值，缺少该键时返回默认值
     */
    public char addTo(K key, char incr) {
        int i = key.ordinal();
        char old = vals[i];
        if (old == (char) 0) {

            char sum = (char) (defRetValue + incr);
            if (sum == (char) 0) return defRetValue;
            vals[i] = sum;
            size++;
            return defRetValue;
        }
        char sum = (char) (old + incr);
        vals[i] = sum;
        if (sum == (char) 0) size--;
        return old;
    }

    @Override
    public char mergeChar(K key, char value, CharBinaryOperator remappingFunction) {
        int i = key.ordinal();
        char old = vals[i];
        char newValue = old == (char) 0 ? value : remappingFunction.apply(old, value);
        if (newValue == (char) 0) {
            if (old != (char) 0) {
                vals[i] = (char) 0;
                size--;
            }
            return defRetValue;
        }
        if (old == (char) 0) size++;
        vals[i] = newValue;
        return newValue;
    }

    /** 同类型容器可直接沿值数组遍历。 */
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Map<?, ?> m)) return false;
        if (m.size() != size) return false;
        if (o instanceof Enum2CharMap<?> other && other.keyType == keyType) {
            return Arrays.equals(vals, other.vals);
        }
        final var vals = this.vals;
        final var keys = this.keys;
        for (int i = 0, n = vals.length; i < n; i++) {
            char v = vals[i];
            if (v != (char) 0) {
                Object ov = m.get(keys[i]);
                if (!(ov instanceof Character cv) || cv.charValue() != v) return false;
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
            char v = vals[i];
            if (v != (char) 0) h += keys[i].hashCode() ^ v;
        }
        return h;
    }

    /** 同类型容器可直接沿值数组遍历。 */
    @Override
    public void putAll(Map<? extends K, ? extends Character> m) {
        if (m instanceof Enum2CharMap<?> other && other.keyType == keyType) {
            if (other.size == 0) return;
            final char[] src = other.vals;
            final char[] dst = this.vals;
            int sz = this.size;
            for (int i = 0, n = dst.length; i < n; i++) {
                char v = src[i];
                if (v != (char) 0) {
                    if (dst[i] == (char) 0) sz++;
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

    private final class EntrySet extends AbstractObjectSet<Reference2CharMap.Entry<K>>
                                 implements Reference2CharMap.FastEntrySet<K> {

        @Override
        public ObjectIterator<Reference2CharMap.Entry<K>> iterator() {
            return new EntryIterator();
        }

        @Override
        public ObjectIterator<Reference2CharMap.Entry<K>> fastIterator() {
            return new EntryIterator();
        }

        /** 迭代复用当前条目，下一次移动时内容会更新。 */
        @Override
        public void fastForEach(Consumer<? super Reference2CharMap.Entry<K>> consumer) {
            Entry entry = new Entry();
            final var vals = Enum2CharMap.this.vals;
            final int length = vals.length;
            for (int i = 0; i < length; i++) {
                if (vals[i] != (char) 0) {
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
            Enum2CharMap.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            if (!isValidKey(e.getKey())) return false;
            int i = ((Enum<?>) e.getKey()).ordinal();
            Object ov = e.getValue();
            return vals[i] != (char) 0 && ov instanceof Character cv && cv.charValue() == vals[i];
        }

        @Override
        public boolean remove(Object o) {
            if (!contains(o)) return false;
            removeChar(((Map.Entry<?, ?>) o).getKey());
            return true;
        }
    }

    private final class Entry implements Reference2CharMap.Entry<K> {

        private int index = -1;

        @Override
        public K getKey() {
            return keys[index];
        }

        @Override
        public char getCharValue() {
            return vals[index];
        }

        @Override
        public char setValue(char value) {
            char old = vals[index];
            vals[index] = value;
            if (value == (char) 0) {
                if (old != (char) 0) size--;
            } else if (old == (char) 0) {
                size++;
            }
            return old;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            Object ov = e.getValue();
            return e.getKey() == keys[index] && ov instanceof Character cv && cv.charValue() == vals[index];
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
            final var vals = Enum2CharMap.this.vals;
            final int length = vals.length;
            while (cursor < length && vals[cursor] == (char) 0) {
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
            if (vals[current] != (char) 0) {
                vals[current] = (char) 0;
                size--;
            }
            current = -1;
        }
    }

    private final class EntryIterator extends IndexIterator implements ObjectIterator<Reference2CharMap.Entry<K>> {

        private final Entry entry = new Entry();

        @Override
        public Reference2CharMap.Entry<K> next() {
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

    private final class ValueIterator extends IndexIterator implements CharIterator {

        @Override
        public char nextChar() {
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
            Enum2CharMap.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            return containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            if (!containsKey(o)) return false;
            Enum2CharMap.this.removeChar(o);
            return true;
        }
    }

    private final class Values extends AbstractCharCollection {

        @Override
        public CharIterator iterator() {
            return new ValueIterator();
        }

        @Override
        public boolean contains(char value) {
            return Enum2CharMap.this.containsValue(value);
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void clear() {
            Enum2CharMap.this.clear();
        }
    }
}
