package com.modularmc.mmclutils.api.collection.map.enums;

import it.unimi.dsi.fastutil.booleans.AbstractBooleanCollection;
import it.unimi.dsi.fastutil.booleans.BooleanCollection;
import it.unimi.dsi.fastutil.booleans.BooleanIterator;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.AbstractReference2BooleanMap;
import it.unimi.dsi.fastutil.objects.AbstractReferenceSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2BooleanFunction;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** 用枚举序号直接寻址的映射。按枚举常量的序号定位，缺失时返回配置的默认值。 迭代器可复用同一个可变条目。 */
public final class Enum2BooleanMap<K extends Enum<K>> extends AbstractReference2BooleanMap<K> {

    private final Class<K> keyType;
    private final K[] keys;
    private final boolean[] vals;
    private int size;

    private final EntrySet entrySet = new EntrySet();
    private final ReferenceSet<K> keySet = new KeySet();
    private final BooleanCollection values = new Values();

    /** 根据参数构造容器。 */
    public Enum2BooleanMap(Class<K> keyType) {
        this.keyType = keyType;
        this.keys = EnumMaps.universe(keyType);
        this.vals = new boolean[keys.length];
    }

    /** 复制映射的数据和配置。 */
    public Enum2BooleanMap(Enum2BooleanMap<K> map) {
        this.keyType = map.keyType;
        this.keys = map.keys;
        this.vals = map.vals.clone();
        this.size = map.size;
    }

    public Class<K> keyType() {
        return keyType;
    }

    @Override
    public boolean getBoolean(Object key) {
        if (!isValidKey(key)) return defRetValue;
        boolean v = vals[((Enum<?>) key).ordinal()];
        return v ? v : defRetValue;
    }

    @Override
    public boolean getOrDefault(Object key, boolean defaultValue) {
        if (!isValidKey(key)) return defaultValue;
        boolean v = vals[((Enum<?>) key).ordinal()];
        return v ? v : defaultValue;
    }

    /** 写入零值会移除映射，缺失时返回默认返回值。 */
    @Override
    public boolean put(K key, boolean value) {
        int i = key.ordinal();
        boolean old = vals[i];
        vals[i] = value;
        if (!value) {
            if (old) size--;
        } else if (!old) {
            size++;
        }
        return old ? old : defRetValue;
    }

    @Override
    public boolean removeBoolean(Object key) {
        if (!isValidKey(key)) return defRetValue;
        int i = ((Enum<?>) key).ordinal();
        boolean old = vals[i];
        if (!old) return defRetValue;
        vals[i] = false;
        size--;
        return old;
    }

    @Override
    public boolean containsKey(Object key) {
        return isValidKey(key) && vals[((Enum<?>) key).ordinal()];
    }

    @Override
    public boolean containsValue(boolean value) {
        return value && size > 0;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void clear() {
        if (size == 0) return;
        Arrays.fill(vals, false);
        size = 0;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super Boolean> action) {
        final var vals = Enum2BooleanMap.this.vals;
        final int length = vals.length;
        for (int i = 0; i < length; i++) {
            if (vals[i]) action.accept(keys[i], Boolean.valueOf(vals[i]));
        }
    }

    @Override
    public FastEntrySet<K> reference2BooleanEntrySet() {
        return entrySet;
    }

    @Override
    public ReferenceSet<K> keySet() {
        return keySet;
    }

    @Override
    public BooleanCollection values() {
        return values;
    }

    @Override
    public boolean putIfAbsent(K key, boolean value) {
        int i = key.ordinal();
        boolean old = vals[i];
        if (old) return old;
        if (value) {
            vals[i] = value;
            size++;
        }
        return defRetValue;
    }

    @Override
    public boolean remove(Object key, boolean value) {
        if (!value || !isValidKey(key)) return false;
        int i = ((Enum<?>) key).ordinal();
        if (vals[i] != value) return false;
        vals[i] = false;
        size--;
        return true;
    }

    @Override
    public boolean replace(K key, boolean oldValue, boolean newValue) {
        if (!oldValue || vals[key.ordinal()] != oldValue) return false;
        put(key, newValue);
        return true;
    }

    @Override
    public boolean replace(K key, boolean value) {
        boolean old = vals[key.ordinal()];
        if (!old) return defRetValue;
        put(key, value);
        return old;
    }

    @Override
    public boolean computeIfAbsent(K key, Predicate<? super K> mappingFunction) {
        int i = key.ordinal();
        boolean v = vals[i];
        if (v) return v;
        boolean newValue = mappingFunction.test(key);
        if (newValue) {
            vals[i] = newValue;
            size++;
        }
        return newValue;
    }

    @Override
    public boolean computeIfAbsent(K key, Reference2BooleanFunction<? super K> mappingFunction) {
        int i = key.ordinal();
        boolean v = vals[i];
        if (v) return v;
        boolean newValue = mappingFunction.getBoolean(key);
        if (newValue) {
            vals[i] = newValue;
            size++;
        }
        return newValue;
    }

    @Override
    public boolean computeBooleanIfPresent(K key, BiFunction<? super K, ? super Boolean, ? extends Boolean> remappingFunction) {
        int i = key.ordinal();
        boolean old = vals[i];
        if (!old) return defRetValue;
        Boolean newValue = remappingFunction.apply(key, Boolean.valueOf(old));
        if (newValue == null) {
            vals[i] = false;
            size--;
            return defRetValue;
        }
        put(key, newValue);
        return newValue;
    }

    @Override
    public boolean computeBoolean(K key, BiFunction<? super K, ? super Boolean, ? extends Boolean> remappingFunction) {
        int i = key.ordinal();
        boolean old = vals[i];
        Boolean newValue = remappingFunction.apply(key, !old ? null : Boolean.valueOf(old));
        if (newValue == null) {
            if (old) {
                vals[i] = false;
                size--;
            }
            return defRetValue;
        }
        put(key, newValue);
        return newValue;
    }

    /** 同类型容器可直接沿值数组遍历。 */
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Map<?, ?> m)) return false;
        if (m.size() != size) return false;
        if (o instanceof Enum2BooleanMap<?> other && other.keyType == keyType) {
            return Arrays.equals(vals, other.vals);
        }
        final var vals = this.vals;
        final var keys = this.keys;
        for (int i = 0, n = vals.length; i < n; i++) {
            if (vals[i]) {
                Object ov = m.get(keys[i]);
                if (!(ov instanceof Boolean bv) || !bv.booleanValue()) return false;
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
            if (vals[i]) h += keys[i].hashCode() ^ 1231;
        }
        return h;
    }

    /** 同类型容器可直接沿值数组遍历。 */
    @Override
    public void putAll(Map<? extends K, ? extends Boolean> m) {
        if (m instanceof Enum2BooleanMap<?> other && other.keyType == keyType) {
            if (other.size == 0) return;
            final boolean[] src = other.vals;
            final boolean[] dst = this.vals;
            int sz = this.size;
            for (int i = 0, n = dst.length; i < n; i++) {
                if (src[i]) {
                    if (!dst[i]) sz++;
                    dst[i] = true;
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

    private final class EntrySet extends AbstractObjectSet<Reference2BooleanMap.Entry<K>>
                                 implements Reference2BooleanMap.FastEntrySet<K> {

        @Override
        public ObjectIterator<Reference2BooleanMap.Entry<K>> iterator() {
            return new EntryIterator();
        }

        @Override
        public ObjectIterator<Reference2BooleanMap.Entry<K>> fastIterator() {
            return new EntryIterator();
        }

        /** 迭代复用当前条目，下一次移动时内容会更新。 */
        @Override
        public void fastForEach(Consumer<? super Reference2BooleanMap.Entry<K>> consumer) {
            Entry entry = new Entry();
            final var vals = Enum2BooleanMap.this.vals;
            final int length = vals.length;
            for (int i = 0; i < length; i++) {
                if (vals[i]) {
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
            Enum2BooleanMap.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            if (!isValidKey(e.getKey())) return false;
            int i = ((Enum<?>) e.getKey()).ordinal();
            Object ov = e.getValue();
            return vals[i] && ov instanceof Boolean bv && bv.booleanValue();
        }

        @Override
        public boolean remove(Object o) {
            if (!contains(o)) return false;
            removeBoolean(((Map.Entry<?, ?>) o).getKey());
            return true;
        }
    }

    private final class Entry implements Reference2BooleanMap.Entry<K> {

        private int index = -1;

        @Override
        public K getKey() {
            return keys[index];
        }

        @Override
        public boolean getBooleanValue() {
            return vals[index];
        }

        @Override
        public boolean setValue(boolean value) {
            boolean old = vals[index];
            vals[index] = value;
            if (!value) {
                if (old) size--;
            } else if (!old) {
                size++;
            }
            return old;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            Object ov = e.getValue();
            return e.getKey() == keys[index] && ov instanceof Boolean bv && bv.booleanValue() == vals[index];
        }

        @Override
        public int hashCode() {
            return keys[index].hashCode() ^ Boolean.hashCode(vals[index]);
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
            final var vals = Enum2BooleanMap.this.vals;
            final int length = vals.length;
            while (cursor < length && !vals[cursor]) {
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
            if (vals[current]) {
                vals[current] = false;
                size--;
            }
            current = -1;
        }
    }

    private final class EntryIterator extends IndexIterator implements ObjectIterator<Reference2BooleanMap.Entry<K>> {

        private final Entry entry = new Entry();

        @Override
        public Reference2BooleanMap.Entry<K> next() {
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

    private final class ValueIterator extends IndexIterator implements BooleanIterator {

        @Override
        public boolean nextBoolean() {
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
            Enum2BooleanMap.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            return containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            if (!containsKey(o)) return false;
            Enum2BooleanMap.this.removeBoolean(o);
            return true;
        }
    }

    private final class Values extends AbstractBooleanCollection {

        @Override
        public BooleanIterator iterator() {
            return new ValueIterator();
        }

        @Override
        public boolean contains(boolean value) {
            return Enum2BooleanMap.this.containsValue(value);
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void clear() {
            Enum2BooleanMap.this.clear();
        }
    }
}
