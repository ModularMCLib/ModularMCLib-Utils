package com.modularmc.mmclutils.collection.map;

import com.modularmc.mmclutils.collection.map.hash.Object2LongOpenCachedHashMap;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;

import java.util.Arrays;

/** 无装箱计数表，分别支持相等键和引用键。 */
public final class LongCounters {

    private LongCounters() {}

    public static <K> ByEquality<K> byEquality() {
        return new ByEquality<>(16);
    }

    public static <K> ByEquality<K> byEquality(int expected) {
        return new ByEquality<>(expected);
    }

    public static <K> ByEquality<K> byEquality(Object2LongMap<K> source) {
        var counter = new ByEquality<K>(source.size());
        source.object2LongEntrySet().forEach(entry -> counter.counts.put(entry.getKey(), entry.getLongValue()));
        return counter;
    }

    public static <K> ByIdentity<K> byIdentity() {
        return new ByIdentity<>(16);
    }

    public static <K> ByIdentity<K> byIdentity(int expected) {
        return new ByIdentity<>(expected);
    }

    private static long added(long current, long delta) {
        long result = current + delta;
        return (current | delta) >= 0 && result < 0 ? Long.MAX_VALUE : result;
    }

    /** 按相等性计数并保存键的哈希值。迭代条目只在当前步有效。 */
    public static final class ByEquality<K> implements Iterable<Object2LongMap.Entry<K>> {

        private final Object2LongOpenCachedHashMap<K> counts;

        private ByEquality(int expected) {
            counts = new Object2LongOpenCachedHashMap<>(expected);
        }

        public Object2LongMap<K> values() {
            return counts;
        }

        /** 返回累加前的值。正溢出时饱和到 long 的最大值。空键累加返回零。 */
        public long addTo(K key, long delta) {
            if (key == null) return 0;
            long old = counts.getLong(key);
            counts.put(key, added(old, delta));
            return old;
        }

        public void ensureAdditionalCapacity(int additional) {
            if (additional < 0) throw new IllegalArgumentException("预留容量不得为负数");
            counts.ensureCapacity(Math.addExact(counts.size(), additional));
        }

        public void reset() {
            counts.resetValues();
        }

        public void removeZeros() {
            counts.object2LongEntrySet().removeIf(entry -> entry.getLongValue() == 0);
        }

        @Override
        public ObjectIterator<Object2LongMap.Entry<K>> iterator() {
            return counts.object2LongEntrySet().fastIterator();
        }
    }

    /** 按引用身份计数。迭代条目只在当前步有效。 */
    public static final class ByIdentity<K> extends Reference2LongOpenHashMap<K> implements Iterable<Reference2LongMap.Entry<K>> {

        private ByIdentity(int expected) {
            super(expected);
        }

        @Override
        public long addTo(K key, long delta) {
            if (key == null) return 0;
            long old = getOrDefault(key, 0);
            put(key, added(old, delta));
            return old;
        }

        public void ensureAdditionalCapacity(int additional) {
            if (additional < 0) throw new IllegalArgumentException("预留容量不得为负数");
            ensureCapacity(Math.addExact(size(), additional));
        }

        public void reset() {
            Arrays.fill(value, 0);
        }

        public void removeZeros() {
            reference2LongEntrySet().removeIf(entry -> entry.getLongValue() == 0);
        }

        @Override
        public ObjectIterator<Reference2LongMap.Entry<K>> iterator() {
            return reference2LongEntrySet().fastIterator();
        }
    }
}
