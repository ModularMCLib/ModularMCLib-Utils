package com.modularmc.mmclutils.api.function;

import com.modularmc.mmclutils.api.collection.cache.ConcurrentWeakIdentityHashMap;

import com.google.common.base.Suppliers;
import lombok.Getter;
import org.apache.commons.lang3.function.TriFunction;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/** 为供应器和一元、多元函数构造按需计算的缓存包装。缓存策略决定参数的比较与保留方式。 */
public class Memoization {

    /**
     * 返回供单线程使用的记忆化 Supplier。并发访问时可使用 Guava 的 {@link Suppliers#memoize memoize}。
     *
     * @param delegate 需要记忆化的 Supplier
     * @return 记忆化后的 Supplier
     */
    public static <T> MemoizedSupplier<T> memoize(Supplier<T> delegate) {
        return new MemoizedSupplier<>(delegate);
    }

    /**
     * 用弱身份键记忆化一元函数。
     *
     * @param memoFunction 负责计算结果的函数，返回空值时不缓存该结果
     * @return 按参数对象身份查找且弱持有参数的函数
     */
    public static <T, R> MemoizedFunction<T, R> memoizeFunctionWeakIdent(final Function<T, R> memoFunction) {
        return new MemoizedFunction<>() {

            @Getter
            private final Map<T, R> cache = new ConcurrentWeakIdentityHashMap<>();

            @Override
            public R apply(T key) {
                return this.cache.computeIfAbsent(key, memoFunction);
            }

            @Override
            public String toString() {
                return "memoizeFunctionWeakIdent/1[function=" + memoFunction + ", size=" + this.cache.size() + "]";
            }
        };
    }

    /**
     * 用弱身份键记忆化二元函数。两个参数分别作为弱身份键，短暂创建的 Pair 不会成为缓存键。
     *
     * @param memoBiFunction 负责计算结果的函数，返回空值时不缓存该结果
     * @return 按两个参数对象身份查找的记忆化函数
     */
    public static <T, U,
            R> MemoizedBiFunction<T, U, R> memoizeFunctionWeakIdent(final BiFunction<T, U, R> memoBiFunction) {
        return new MemoizedBiFunction<>() {

            private final WeakIdentityPairCache<T, U, R> cache = new WeakIdentityPairCache<>();

            @Override
            public Map<Pair<T, U>, R> getCache() {
                return cache;
            }

            @Override
            public R apply(T key1, U key2) {
                return cache.computeIfAbsent(key1, key2, () -> memoBiFunction.apply(key1, key2));
            }

            @Override
            public String toString() {
                return "memoizeFunctionWeakIdent/2[function=" + memoBiFunction + ", size=" + this.cache.size() + "]";
            }
        };
    }

    /**
     * 用强键记忆化三元函数，参数元组按 {@link Triple} 的值相等性比较。
     *
     * @param memoTriFunction 负责计算结果的函数，返回空值时不缓存该结果
     * @return 保留已使用参数元组的记忆化函数
     */
    public static <T, U, V, R> MemoizedTriFunction<T, U, V, R> memoize(final TriFunction<T, U, V, R> memoTriFunction) {
        return new MemoizedTriFunction<>() {

            @Getter
            private final Map<Triple<T, U, V>, R> cache = new ConcurrentHashMap<>();

            @Override
            public R apply(T key1, U key2, V key3) {
                return this.cache.computeIfAbsent(Triple.of(key1, key2, key3), (cacheKey) -> {
                    return memoTriFunction.apply(cacheKey.getLeft(), cacheKey.getMiddle(), cacheKey.getRight());
                });
            }

            @Override
            public String toString() {
                return "memoize/3[function=" + memoTriFunction + ", size=" + this.cache.size() + "]";
            }
        };
    }

    /** 通过按层嵌套的弱身份映射，为一对对象提供 Map 视图。 */
    private static final class WeakIdentityPairCache<T, U, R> extends AbstractMap<Pair<T, U>, R> {

        private final ConcurrentWeakIdentityHashMap<T, ConcurrentWeakIdentityHashMap<U, R>> values = new ConcurrentWeakIdentityHashMap<>();

        R computeIfAbsent(T first, U second, Supplier<? extends R> factory) {
            ConcurrentWeakIdentityHashMap<U, R> entries = values.computeIfAbsent(first, ignored -> new ConcurrentWeakIdentityHashMap<>());
            return entries.computeIfAbsent(second, ignored -> factory.get());
        }

        @Override
        public boolean containsKey(Object key) {
            if (!(key instanceof Pair<?, ?> pair)) return false;
            ConcurrentWeakIdentityHashMap<U, R> entries = values.get(pair.getLeft());
            return entries != null && entries.containsKey(pair.getRight());
        }

        @Override
        public R get(Object key) {
            if (!(key instanceof Pair<?, ?> pair)) return null;
            ConcurrentWeakIdentityHashMap<U, R> entries = values.get(pair.getLeft());
            return entries == null ? null : entries.get(pair.getRight());
        }

        @Override
        public R put(Pair<T, U> key, R value) {
            ConcurrentWeakIdentityHashMap<U, R> entries = values.computeIfAbsent(key.getLeft(), ignored -> new ConcurrentWeakIdentityHashMap<>());
            return entries.put(key.getRight(), value);
        }

        @Override
        public R remove(Object key) {
            if (!(key instanceof Pair<?, ?> pair)) return null;
            ConcurrentWeakIdentityHashMap<U, R> entries = values.get(pair.getLeft());
            return entries == null ? null : entries.remove(pair.getRight());
        }

        @Override
        public int size() {
            long size = 0;
            for (ConcurrentWeakIdentityHashMap<U, R> entries : values.values()) size += entries.size();
            return (int) Math.min(size, Integer.MAX_VALUE);
        }

        @Override
        public void clear() {
            values.clear();
        }

        @Override
        public Set<Map.Entry<Pair<T, U>, R>> entrySet() {
            return new AbstractSet<>() {

                @Override
                public Iterator<Map.Entry<Pair<T, U>, R>> iterator() {
                    Iterator<Map.Entry<T, ConcurrentWeakIdentityHashMap<U, R>>> outer = values.entrySet().iterator();
                    return new Iterator<>() {

                        private T first;
                        private ConcurrentWeakIdentityHashMap<U, R> entries;
                        private Iterator<Map.Entry<U, R>> inner = Map.<U, R>of().entrySet().iterator();
                        private Map.Entry<U, R> current;

                        @Override
                        public boolean hasNext() {
                            while (!inner.hasNext() && outer.hasNext()) {
                                Map.Entry<T, ConcurrentWeakIdentityHashMap<U, R>> entry = outer.next();
                                first = entry.getKey();
                                entries = entry.getValue();
                                inner = entries.entrySet().iterator();
                            }
                            return inner.hasNext();
                        }

                        @Override
                        public Map.Entry<Pair<T, U>, R> next() {
                            if (!hasNext()) throw new NoSuchElementException();
                            current = inner.next();
                            Pair<T, U> key = Pair.of(first, current.getKey());
                            return new SimpleEntry<>(key, current.getValue()) {

                                @Override
                                public R setValue(R value) {
                                    R previous = WeakIdentityPairCache.this.put(getKey(), value);
                                    super.setValue(value);
                                    return previous;
                                }
                            };
                        }

                        @Override
                        public void remove() {
                            if (current == null) throw new IllegalStateException();
                            entries.remove(current.getKey());
                            current = null;
                        }
                    };
                }

                @Override
                public int size() {
                    return WeakIdentityPairCache.this.size();
                }
            };
        }
    }
}
