package com.modularmc.mmclutils.collection.map.enums;

import it.unimi.dsi.fastutil.doubles.AbstractDoubleCollection;
import it.unimi.dsi.fastutil.doubles.DoubleCollection;
import it.unimi.dsi.fastutil.doubles.DoubleIterator;
import it.unimi.dsi.fastutil.objects.*;

import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.ToDoubleFunction;

/** 用枚举序号直接寻址的映射。槽位的零值表示缺少该键；读取缺失键时返回配置的默认值。 迭代器可复用同一个可变条目。 */
public final class Enum2DoubleMap<K extends Enum<K>> extends AbstractReference2DoubleMap<K> {

    private final Class<K> keyType;
    private final K[] keys;
    private final double[] vals;
    private int size;

    private final EntrySet entrySet = new EntrySet();
    private final ReferenceSet<K> keySet = new KeySet();
    private final DoubleCollection values = new Values();

    /** 根据参数构造容器。 */
    public Enum2DoubleMap(Class<K> keyType) {
        this.keyType = keyType;
        this.keys = EnumMaps.universe(keyType);
        this.vals = new double[keys.length];
    }

    /** 复制映射的数据和配置。 */
    public Enum2DoubleMap(Enum2DoubleMap<K> map) {
        this.keyType = map.keyType;
        this.keys = map.keys;
        this.vals = map.vals.clone();
        this.size = map.size;
    }

    public Class<K> keyType() {
        return keyType;
    }

    @Override
    public double getDouble(Object key) {
        if (!isValidKey(key)) return defRetValue;
        double v = vals[((Enum<?>) key).ordinal()];
        return v != 0D ? v : defRetValue;
    }

    @Override
    public double getOrDefault(Object key, double defaultValue) {
        if (!isValidKey(key)) return defaultValue;
        double v = vals[((Enum<?>) key).ordinal()];
        return v != 0D ? v : defaultValue;
    }

    /** 写入零值会移除映射，缺失时返回默认返回值。 */
    @Override
    public double put(K key, double value) {
        int i = key.ordinal();
        double old = vals[i];
        vals[i] = value;
        if (value == 0D) {
            if (old != 0D) size--;
        } else if (old == 0D) {
            size++;
        }
        return old != 0D ? old : defRetValue;
    }

    @Override
    public double removeDouble(Object key) {
        if (!isValidKey(key)) return defRetValue;
        int i = ((Enum<?>) key).ordinal();
        double old = vals[i];
        if (old == 0D) return defRetValue;
        vals[i] = 0D;
        size--;
        return old;
    }

    @Override
    public boolean containsKey(Object key) {
        return isValidKey(key) && vals[((Enum<?>) key).ordinal()] != 0D;
    }

    @Override
    public boolean containsValue(double value) {
        if (value == 0D) return false;
        final long bits = Double.doubleToLongBits(value);
        final var vals = Enum2DoubleMap.this.vals;
        final int length = vals.length;
        for (int i = 0; i < length; i++) {
            if (Double.doubleToLongBits(vals[i]) == bits) return true;
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
        Arrays.fill(vals, 0D);
        size = 0;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super Double> action) {
        final var vals = Enum2DoubleMap.this.vals;
        final int length = vals.length;
        for (int i = 0; i < length; i++) {
            if (vals[i] != 0D) action.accept(keys[i], Double.valueOf(vals[i]));
        }
    }

    @Override
    public FastEntrySet<K> reference2DoubleEntrySet() {
        return entrySet;
    }

    @Override
    public ReferenceSet<K> keySet() {
        return keySet;
    }

    @Override
    public DoubleCollection values() {
        return values;
    }

    @Override
    public double putIfAbsent(K key, double value) {
        int i = key.ordinal();
        double old = vals[i];
        if (old != 0D) return old;
        if (value != 0D) {
            vals[i] = value;
            size++;
        }
        return defRetValue;
    }

    @Override
    public boolean remove(Object key, double value) {
        if (value == 0D || !isValidKey(key)) return false;
        int i = ((Enum<?>) key).ordinal();
        if (vals[i] != value) return false;
        vals[i] = 0D;
        size--;
        return true;
    }

    @Override
    public boolean replace(K key, double oldValue, double newValue) {
        if (oldValue == 0D || vals[key.ordinal()] != oldValue) return false;
        put(key, newValue);
        return true;
    }

    @Override
    public double replace(K key, double value) {
        double old = vals[key.ordinal()];
        if (old == 0D) return defRetValue;
        put(key, value);
        return old;
    }

    @Override
    public double computeIfAbsent(K key, ToDoubleFunction<? super K> mappingFunction) {
        int i = key.ordinal();
        double v = vals[i];
        if (v != 0D) return v;
        double newValue = mappingFunction.applyAsDouble(key);
        if (newValue != 0D) {
            vals[i] = newValue;
            size++;
        }
        return newValue;
    }

    @Override
    public double computeIfAbsent(K key, Reference2DoubleFunction<? super K> mappingFunction) {
        int i = key.ordinal();
        double v = vals[i];
        if (v != 0D) return v;
        double newValue = mappingFunction.getDouble(key);
        if (newValue != 0D) {
            vals[i] = newValue;
            size++;
        }
        return newValue;
    }

    @Override
    public double computeDoubleIfPresent(K key, BiFunction<? super K, ? super Double, ? extends Double> remappingFunction) {
        int i = key.ordinal();
        double old = vals[i];
        if (old == 0D) return defRetValue;
        Double newValue = remappingFunction.apply(key, Double.valueOf(old));
        if (newValue == null) {
            vals[i] = 0D;
            size--;
            return defRetValue;
        }
        put(key, newValue);
        return newValue;
    }

    @Override
    public double computeDouble(K key, BiFunction<? super K, ? super Double, ? extends Double> remappingFunction) {
        int i = key.ordinal();
        double old = vals[i];
        Double newValue = remappingFunction.apply(key, old == 0D ? null : Double.valueOf(old));
        if (newValue == null) {
            if (old != 0D) {
                vals[i] = 0D;
                size--;
            }
            return defRetValue;
        }
        put(key, newValue);
        return newValue;
    }

    public double addTo(K key, double incr) {
        int i = key.ordinal();
        double old = vals[i];
        if (old == 0D) {

            double sum = defRetValue + incr;
            if (sum == 0D) return defRetValue;
            vals[i] = sum;
            size++;
            return defRetValue;
        }
        double sum = old + incr;
        vals[i] = sum;
        if (sum == 0D) size--;
        return old;
    }

    @Override
    public double mergeDouble(K key, double value, DoubleBinaryOperator remappingFunction) {
        int i = key.ordinal();
        double old = vals[i];
        double newValue = old == 0D ? value : remappingFunction.applyAsDouble(old, value);
        if (newValue == 0D) {
            if (old != 0D) {
                vals[i] = 0D;
                size--;
            }
            return defRetValue;
        }
        if (old == 0D) size++;
        vals[i] = newValue;
        return newValue;
    }

    /** 同类型容器可直接沿值数组遍历。 */
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Map<?, ?> m)) return false;
        if (m.size() != size) return false;
        if (o instanceof Enum2DoubleMap<?> other && other.keyType == keyType) {
            return Arrays.equals(vals, other.vals);
        }
        final var vals = this.vals;
        final var keys = this.keys;
        for (int i = 0, n = vals.length; i < n; i++) {
            double v = vals[i];
            if (v != 0D) {
                Object ov = m.get(keys[i]);
                if (!(ov instanceof Double dv) || Double.doubleToLongBits(dv.doubleValue()) != Double.doubleToLongBits(v)) return false;
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
            double v = vals[i];
            if (v != 0D) h += keys[i].hashCode() ^ Double.hashCode(v);
        }
        return h;
    }

    /** 同类型容器可直接沿值数组遍历。 */
    @Override
    public void putAll(Map<? extends K, ? extends Double> m) {
        if (m instanceof Enum2DoubleMap<?> other && other.keyType == keyType) {
            if (other.size == 0) return;
            final double[] src = other.vals;
            final double[] dst = this.vals;
            int sz = this.size;
            for (int i = 0, n = dst.length; i < n; i++) {
                double v = src[i];
                if (v != 0D) {
                    if (dst[i] == 0D) sz++;
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

    private final class EntrySet extends AbstractObjectSet<Reference2DoubleMap.Entry<K>>
                                 implements Reference2DoubleMap.FastEntrySet<K> {

        @Override
        public ObjectIterator<Reference2DoubleMap.Entry<K>> iterator() {
            return new EntryIterator();
        }

        @Override
        public ObjectIterator<Reference2DoubleMap.Entry<K>> fastIterator() {
            return new EntryIterator();
        }

        /** 迭代复用当前条目，下一次移动时内容会更新。 */
        @Override
        public void fastForEach(Consumer<? super Reference2DoubleMap.Entry<K>> consumer) {
            Entry entry = new Entry();
            final var vals = Enum2DoubleMap.this.vals;
            final int length = vals.length;
            for (int i = 0; i < length; i++) {
                if (vals[i] != 0D) {
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
            Enum2DoubleMap.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            if (!isValidKey(e.getKey())) return false;
            int i = ((Enum<?>) e.getKey()).ordinal();
            Object ov = e.getValue();
            return vals[i] != 0D && ov instanceof Double dv && Double.doubleToLongBits(dv.doubleValue()) == Double.doubleToLongBits(vals[i]);
        }

        @Override
        public boolean remove(Object o) {
            if (!contains(o)) return false;
            removeDouble(((Map.Entry<?, ?>) o).getKey());
            return true;
        }
    }

    private final class Entry implements Reference2DoubleMap.Entry<K> {

        private int index = -1;

        @Override
        public K getKey() {
            return keys[index];
        }

        @Override
        public double getDoubleValue() {
            return vals[index];
        }

        @Override
        public double setValue(double value) {
            double old = vals[index];
            vals[index] = value;
            if (value == 0D) {
                if (old != 0D) size--;
            } else if (old == 0D) {
                size++;
            }
            return old;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            Object ov = e.getValue();
            return e.getKey() == keys[index] && ov instanceof Double dv && Double.doubleToLongBits(dv.doubleValue()) == Double.doubleToLongBits(vals[index]);
        }

        @Override
        public int hashCode() {
            return keys[index].hashCode() ^ Double.hashCode(vals[index]);
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
            final var vals = Enum2DoubleMap.this.vals;
            final int length = vals.length;
            while (cursor < length && vals[cursor] == 0D) {
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
            if (vals[current] != 0D) {
                vals[current] = 0D;
                size--;
            }
            current = -1;
        }
    }

    private final class EntryIterator extends IndexIterator implements ObjectIterator<Reference2DoubleMap.Entry<K>> {

        private final Entry entry = new Entry();

        @Override
        public Reference2DoubleMap.Entry<K> next() {
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

    private final class ValueIterator extends IndexIterator implements DoubleIterator {

        @Override
        public double nextDouble() {
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
            Enum2DoubleMap.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            return containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            if (!containsKey(o)) return false;
            Enum2DoubleMap.this.removeDouble(o);
            return true;
        }
    }

    private final class Values extends AbstractDoubleCollection {

        @Override
        public DoubleIterator iterator() {
            return new ValueIterator();
        }

        @Override
        public boolean contains(double value) {
            return Enum2DoubleMap.this.containsValue(value);
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void clear() {
            Enum2DoubleMap.this.clear();
        }
    }
}
