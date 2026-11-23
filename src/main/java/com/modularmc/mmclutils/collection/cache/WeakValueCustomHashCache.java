package com.modularmc.mmclutils.collection.cache;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.HashCommon;

import java.util.function.Function;
import java.util.function.UnaryOperator;

/** 按键保存弱引用值，无外部引用的值可由回收线程移除。 计算函数在锁外运行，并发写入在分段锁内合并。 */
public final class WeakValueCustomHashCache<K, V> extends WeakSegmentedCache<WeakValueCustomHashCache.Segment<K, V>> implements LoadingCache<K, V>, ReclaimableCache {

    private final Hash.Strategy<? super K> strategy;
    private final Function<? super K, ? extends V> createFunction;

    /** 使用默认并发度。创建函数由参数决定，可省略。 */
    public WeakValueCustomHashCache(Hash.Strategy<? super K> strategy) {
        this(strategy, SegmentedCache.DEFAULT_CONCURRENCY, null);
    }

    /** 使用默认并发度。创建函数由参数决定，可省略。 */
    public WeakValueCustomHashCache(Hash.Strategy<? super K> strategy, Function<? super K, ? extends V> createFunction) {
        this(strategy, SegmentedCache.DEFAULT_CONCURRENCY, createFunction);
    }

    /** 按给定的并发度初始化缓存。并发度必须大于零。 */
    public WeakValueCustomHashCache(Hash.Strategy<? super K> strategy, int concurrencyLevel) {
        this(strategy, concurrencyLevel, null);
    }

    /** 按给定的并发度初始化缓存。并发度必须大于零。 */
    public WeakValueCustomHashCache(Hash.Strategy<? super K> strategy, int concurrencyLevel, Function<? super K, ? extends V> createFunction) {
        super(concurrencyLevel, i -> new Segment<>(strategy));
        CacheReferenceQueue.add(this);
        this.strategy = strategy;
        this.createFunction = createFunction;
    }

    @Override
    public Function<? super K, ? extends V> createFunction() {
        return createFunction;
    }

    @Override
    public V getCache(final K k, Function<? super K, ? extends V> createFunction) {
        int hash = strategy.hashCode(k);
        int mix = HashCommon.mix(hash);
        return segmentFor(mix).getCache(k, hash, mix, createFunction, Interner.identityMappingFunction());
    }

    @Override
    public V getCache(K k, Function<? super K, ? extends V> createFunction, UnaryOperator<K> keyMappingFunction) {
        int hash = strategy.hashCode(k);
        int mix = HashCommon.mix(hash);
        return segmentFor(mix).getCache(k, hash, mix, createFunction, keyMappingFunction);
    }

    @Override
    public V getCacheRecursive(final K k, Function<? super K, ? extends V> createFunction) {
        int hash = strategy.hashCode(k);
        int mix = HashCommon.mix(hash);
        return segmentFor(mix).getCache(k, hash, mix, createFunction, Interner.identityMappingFunction());
    }

    @Override
    public V getCacheRecursive(final K k, Function<? super K, ? extends V> createFunction, UnaryOperator<K> keyMappingFunction) {
        int hash = strategy.hashCode(k);
        int mix = HashCommon.mix(hash);
        return segmentFor(mix).getCache(k, hash, mix, createFunction, keyMappingFunction);
    }

    @Override
    public V getIfPresent(final K k) {
        int hash = strategy.hashCode(k);
        int mix = HashCommon.mix(hash);
        return segmentFor(mix).getIfAbsent(k, hash, mix);
    }

    @Override
    public V putIfAbsent(final K k, final V v) {
        int hash = strategy.hashCode(k);
        int mix = HashCommon.mix(hash);
        return segmentFor(mix).put(k, v, hash, mix);
    }

    @Override
    public void clear() {
        clearSegments();
    }

    @Override
    public void clearCache() {
        expungeStaleEntries();
    }

    /** 替换已被回收的节点，保留原键所在的链位置。 */
    final static class Segment<K, V> extends WeakHashSegment<WeakReferenceValueNode<K, V>> {

        private final Hash.Strategy<? super K> strategy;

        private Segment(Hash.Strategy<? super K> strategy) {
            this.strategy = strategy;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected WeakReferenceValueNode<K, V>[] newArray(int capacity) {
            return new WeakReferenceValueNode[capacity];
        }

        @Override
        protected int nodeHash(WeakReferenceValueNode<K, V> node) {
            return node.hash;
        }

        /** 替换已被回收的节点，保留原键所在的链位置。 */
        private V getCache(final K k, final int hash, int mix,
                           Function<? super K, ? extends V> createFunction, UnaryOperator<K> keyMappingFunction) {
            long stamp = readLock();
            try {
                WeakReferenceValueNode<K, V> curr = table[mix & mask];
                while (curr != null) {
                    if (curr.hash == hash && (k == curr.key || strategy.equals(k, curr.key))) {
                        V v = curr.get();
                        if (v != null) return v;
                        break;
                    }
                    curr = curr.next;
                }
            } finally {
                unlockRead(stamp);
            }
            // 在锁外执行计算，允许回调再次访问同一缓存。
            final var mapped = keyMappingFunction.apply(k);
            final var v = createFunction.apply(mapped);
            stamp = writeLock();
            try {
                final int index = mix & mask;
                final WeakReferenceValueNode<K, V> node = table[index];
                WeakReferenceValueNode<K, V> prev = null;
                WeakReferenceValueNode<K, V> curr = node;
                while (curr != null) {
                    if (curr.hash == hash && (mapped == curr.key || strategy.equals(mapped, curr.key))) {
                        V existing = curr.get();
                        if (existing != null) {
                            return existing;
                        }
                        // 替换已被回收的节点，保留原键所在的链位置。
                        var n = new WeakReferenceValueNode<>(mapped, v, hash, curr.next, queue);
                        if (prev == null) {
                            table[index] = n;
                        } else {
                            prev.next = n;
                        }
                        return v;
                    }
                    prev = curr;
                    curr = curr.next;
                }
                table[index] = new WeakReferenceValueNode<>(mapped, v, hash, node, queue);
                if (++size > maxFill) {
                    resize();
                }
                return v;
            } finally {
                unlockWrite(stamp);
            }
        }

        /** 只读取现有条目。 */
        private V getIfAbsent(final K k, final int hash, int mix) {
            long stamp = readLock();
            try {
                final int index = mix & mask;
                WeakReferenceValueNode<K, V> curr = table[index];
                while (curr != null) {
                    if (curr.hash == hash && (k == curr.key || strategy.equals(k, curr.key))) {
                        return curr.get();
                    }
                    curr = curr.next;
                }
                return null;
            } finally {
                unlockRead(stamp);
            }
        }

        /** 替换已被回收的节点，保留原键所在的链位置。 */
        private V put(final K k, final V v, final int hash, int mix) {
            long stamp = writeLock();
            try {
                final int index = mix & mask;
                final WeakReferenceValueNode<K, V> node = table[index];
                WeakReferenceValueNode<K, V> prev = null;
                WeakReferenceValueNode<K, V> curr = node;
                while (curr != null) {
                    if (curr.hash == hash && (k == curr.key || strategy.equals(k, curr.key))) {
                        V old = curr.get();
                        if (old != null) return old;
                        // 替换已被回收的节点，保留原键所在的链位置。
                        var n = new WeakReferenceValueNode<>(k, v, hash, curr.next, queue);
                        if (prev == null) {
                            table[index] = n;
                        } else {
                            prev.next = n;
                        }
                        return v;
                    }
                    prev = curr;
                    curr = curr.next;
                }
                table[index] = new WeakReferenceValueNode<>(k, v, hash, node, queue);
                if (++size > maxFill) {
                    resize();
                }
                return v;
            } finally {
                unlockWrite(stamp);
            }
        }
    }
}
