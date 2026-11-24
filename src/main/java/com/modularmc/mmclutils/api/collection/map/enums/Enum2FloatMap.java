package com.modularmc.mmclutils.api.collection.map.enums;

import it.unimi.dsi.fastutil.floats.AbstractFloatCollection;
import it.unimi.dsi.fastutil.floats.FloatBinaryOperator;
import it.unimi.dsi.fastutil.floats.FloatCollection;
import it.unimi.dsi.fastutil.floats.FloatIterator;
import it.unimi.dsi.fastutil.objects.*;

import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

/** 用枚举序号直接寻址的映射。槽位的零值表示缺少该键；读取缺失键时返回配置的默认值。 迭代器可复用同一个可变条目。 */
public final class Enum2FloatMap<K extends Enum<K>> extends AbstractReference2FloatMap<K> {

    private final Class<K> keyType;
    private final K[] keys;
    private final float[] vals;
    private int size;

    private final EntrySet entrySet = new EntrySet();
    private final ReferenceSet<K> keySet = new KeySet();
    private final FloatCollection values = new Values();

    /** 为该枚举的全部常量分配槽位。 */
    public Enum2FloatMap(Class<K> keyType) {
        this.keyType = keyType;
        this.keys = EnumMaps.universe(keyType);
        this.vals = new float[keys.length];
    }

    /** 复制槽位中的条目，使用新映射的初始默认返回值。 */
    public Enum2FloatMap(Enum2FloatMap<K> map) {
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
    public float getFloat(Object key) {
        if (!isValidKey(key)) return defRetValue;
        float v = vals[((Enum<?>) key).ordinal()];
        return v != 0F ? v : defRetValue;
    }

    @Override
    public float getOrDefault(Object key, float defaultValue) {
        if (!isValidKey(key)) return defaultValue;
        float v = vals[((Enum<?>) key).ordinal()];
        return v != 0F ? v : defaultValue;
    }

    /** 写入零值会移除映射，缺失时返回默认返回值。 */
    @Override
    public float put(K key, float value) {
        int i = key.ordinal();
        float old = vals[i];
        vals[i] = value;
        if (value == 0F) {
            if (old != 0F) size--;
        } else if (old == 0F) {
            size++;
        }
        return old != 0F ? old : defRetValue;
    }

    @Override
    public float removeFloat(Object key) {
        if (!isValidKey(key)) return defRetValue;
        int i = ((Enum<?>) key).ordinal();
        float old = vals[i];
        if (old == 0F) return defRetValue;
        vals[i] = 0F;
        size--;
        return old;
    }

    @Override
    public boolean containsKey(Object key) {
        return isValidKey(key) && vals[((Enum<?>) key).ordinal()] != 0F;
    }

    @Override
    public boolean containsValue(float value) {
        if (value == 0F) return false;
        final int bits = Float.floatToIntBits(value);
        final var vals = Enum2FloatMap.this.vals;
        final int length = vals.length;
        for (int i = 0; i < length; i++) {
            if (Float.floatToIntBits(vals[i]) == bits) return true;
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
        Arrays.fill(vals, 0F);
        size = 0;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super Float> action) {
        final var vals = Enum2FloatMap.this.vals;
        final int length = vals.length;
        for (int i = 0; i < length; i++) {
            if (vals[i] != 0F) action.accept(keys[i], Float.valueOf(vals[i]));
        }
    }

    @Override
    public FastEntrySet<K> reference2FloatEntrySet() {
        return entrySet;
    }

    @Override
    public ReferenceSet<K> keySet() {
        return keySet;
    }

    @Override
    public FloatCollection values() {
        return values;
    }

    @Override
    public float putIfAbsent(K key, float value) {
        int i = key.ordinal();
        float old = vals[i];
        if (old != 0F) return old;
        if (value != 0F) {
            vals[i] = value;
            size++;
        }
        return defRetValue;
    }

    @Override
    public boolean remove(Object key, float value) {
        if (value == 0F || !isValidKey(key)) return false;
        int i = ((Enum<?>) key).ordinal();
        if (vals[i] != value) return false;
        vals[i] = 0F;
        size--;
        return true;
    }

    @Override
    public boolean replace(K key, float oldValue, float newValue) {
        if (oldValue == 0F || vals[key.ordinal()] != oldValue) return false;
        put(key, newValue);
        return true;
    }

    @Override
    public float replace(K key, float value) {
        float old = vals[key.ordinal()];
        if (old == 0F) return defRetValue;
        put(key, value);
        return old;
    }

    @Override
    public float computeIfAbsent(K key, ToDoubleFunction<? super K> mappingFunction) {
        int i = key.ordinal();
        float v = vals[i];
        if (v != 0F) return v;
        float newValue = (float) mappingFunction.applyAsDouble(key);
        if (newValue != 0F) {
            vals[i] = newValue;
            size++;
        }
        return newValue;
    }

    @Override
    public float computeIfAbsent(K key, Reference2FloatFunction<? super K> mappingFunction) {
        int i = key.ordinal();
        float v = vals[i];
        if (v != 0F) return v;
        float newValue = mappingFunction.getFloat(key);
        if (newValue != 0F) {
            vals[i] = newValue;
            size++;
        }
        return newValue;
    }

    @Override
    public float computeFloatIfPresent(K key, BiFunction<? super K, ? super Float, ? extends Float> remappingFunction) {
        int i = key.ordinal();
        float old = vals[i];
        if (old == 0F) return defRetValue;
        Float newValue = remappingFunction.apply(key, Float.valueOf(old));
        if (newValue == null) {
            vals[i] = 0F;
            size--;
            return defRetValue;
        }
        put(key, newValue);
        return newValue;
    }

    @Override
    public float computeFloat(K key, BiFunction<? super K, ? super Float, ? extends Float> remappingFunction) {
        int i = key.ordinal();
        float old = vals[i];
        Float newValue = remappingFunction.apply(key, old == 0F ? null : Float.valueOf(old));
        if (newValue == null) {
            if (old != 0F) {
                vals[i] = 0F;
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
    public float addTo(K key, float incr) {
        int i = key.ordinal();
        float old = vals[i];
        if (old == 0F) {

            float sum = defRetValue + incr;
            if (sum == 0F) return defRetValue;
            vals[i] = sum;
            size++;
            return defRetValue;
        }
        float sum = old + incr;
        vals[i] = sum;
        if (sum == 0F) size--;
        return old;
    }

    @Override
    public float mergeFloat(K key, float value, FloatBinaryOperator remappingFunction) {
        int i = key.ordinal();
        float old = vals[i];
        float newValue = old == 0F ? value : remappingFunction.apply(old, value);
        if (newValue == 0F) {
            if (old != 0F) {
                vals[i] = 0F;
                size--;
            }
            return defRetValue;
        }
        if (old == 0F) size++;
        vals[i] = newValue;
        return newValue;
    }

    /** 同类型容器可直接沿值数组遍历。 */
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Map<?, ?> m)) return false;
        if (m.size() != size) return false;
        if (o instanceof Enum2FloatMap<?> other && other.keyType == keyType) {
            return Arrays.equals(vals, other.vals);
        }
        final var vals = this.vals;
        final var keys = this.keys;
        for (int i = 0, n = vals.length; i < n; i++) {
            float v = vals[i];
            if (v != 0F) {
                Object ov = m.get(keys[i]);
                if (!(ov instanceof Float fv) || Float.floatToIntBits(fv.floatValue()) != Float.floatToIntBits(v)) return false;
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
            float v = vals[i];
            if (v != 0F) h += keys[i].hashCode() ^ Float.hashCode(v);
        }
        return h;
    }

    /** 同类型容器可直接沿值数组遍历。 */
    @Override
    public void putAll(Map<? extends K, ? extends Float> m) {
        if (m instanceof Enum2FloatMap<?> other && other.keyType == keyType) {
            if (other.size == 0) return;
            final float[] src = other.vals;
            final float[] dst = this.vals;
            int sz = this.size;
            for (int i = 0, n = dst.length; i < n; i++) {
                float v = src[i];
                if (v != 0F) {
                    if (dst[i] == 0F) sz++;
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

    private final class EntrySet extends AbstractObjectSet<Reference2FloatMap.Entry<K>>
                                 implements Reference2FloatMap.FastEntrySet<K> {

        @Override
        public ObjectIterator<Reference2FloatMap.Entry<K>> iterator() {
            return new EntryIterator();
        }

        @Override
        public ObjectIterator<Reference2FloatMap.Entry<K>> fastIterator() {
            return new EntryIterator();
        }

        /** 迭代复用当前条目，下一次移动时内容会更新。 */
        @Override
        public void fastForEach(Consumer<? super Reference2FloatMap.Entry<K>> consumer) {
            Entry entry = new Entry();
            final var vals = Enum2FloatMap.this.vals;
            final int length = vals.length;
            for (int i = 0; i < length; i++) {
                if (vals[i] != 0F) {
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
            Enum2FloatMap.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            if (!isValidKey(e.getKey())) return false;
            int i = ((Enum<?>) e.getKey()).ordinal();
            Object ov = e.getValue();
            return vals[i] != 0F && ov instanceof Float fv && Float.floatToIntBits(fv.floatValue()) == Float.floatToIntBits(vals[i]);
        }

        @Override
        public boolean remove(Object o) {
            if (!contains(o)) return false;
            removeFloat(((Map.Entry<?, ?>) o).getKey());
            return true;
        }
    }

    private final class Entry implements Reference2FloatMap.Entry<K> {

        private int index = -1;

        @Override
        public K getKey() {
            return keys[index];
        }

        @Override
        public float getFloatValue() {
            return vals[index];
        }

        @Override
        public float setValue(float value) {
            float old = vals[index];
            vals[index] = value;
            if (value == 0F) {
                if (old != 0F) size--;
            } else if (old == 0F) {
                size++;
            }
            return old;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            Object ov = e.getValue();
            return e.getKey() == keys[index] && ov instanceof Float fv && Float.floatToIntBits(fv.floatValue()) == Float.floatToIntBits(vals[index]);
        }

        @Override
        public int hashCode() {
            return keys[index].hashCode() ^ Float.hashCode(vals[index]);
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
            final var vals = Enum2FloatMap.this.vals;
            final int length = vals.length;
            while (cursor < length && vals[cursor] == 0F) {
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
            if (vals[current] != 0F) {
                vals[current] = 0F;
                size--;
            }
            current = -1;
        }
    }

    private final class EntryIterator extends IndexIterator implements ObjectIterator<Reference2FloatMap.Entry<K>> {

        private final Entry entry = new Entry();

        @Override
        public Reference2FloatMap.Entry<K> next() {
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

    private final class ValueIterator extends IndexIterator implements FloatIterator {

        @Override
        public float nextFloat() {
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
            Enum2FloatMap.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            return containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            if (!containsKey(o)) return false;
            Enum2FloatMap.this.removeFloat(o);
            return true;
        }
    }

    private final class Values extends AbstractFloatCollection {

        @Override
        public FloatIterator iterator() {
            return new ValueIterator();
        }

        @Override
        public boolean contains(float value) {
            return Enum2FloatMap.this.containsValue(value);
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void clear() {
            Enum2FloatMap.this.clear();
        }
    }
}
