package com.modularmc.mmclutils.api.collection.cache;

import it.unimi.dsi.fastutil.HashCommon;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.function.LongFunction;

/**
 * 使用原始类型 {@code long} 作为键，弱引用缓存值。值失去外部强引用后，后续查找可能重新创建。
 * 计算函数在分段锁外运行，并发写入在锁内合并。
 *
 * @param <V> 缓存值类型
 */
public final class WeakValueLongCache<V> extends WeakSegmentedCache<WeakValueLongCache.Segment<V>> implements ReclaimableCache {

    private final LongFunction<? extends V> createFunction;

    /** 使用默认并发度。创建函数由参数决定，可省略。 */
    public WeakValueLongCache() {
        this(SegmentedCache.DEFAULT_CONCURRENCY, null);
    }

    /** 使用默认并发度。创建函数由参数决定，可省略。 */
    public WeakValueLongCache(LongFunction<? extends V> createFunction) {
        this(SegmentedCache.DEFAULT_CONCURRENCY, createFunction);
    }

    /** 按给定的并发度初始化缓存。并发度必须大于零。 */
    public WeakValueLongCache(int concurrencyLevel) {
        this(concurrencyLevel, null);
    }

    /** 按给定的并发度初始化缓存。并发度必须大于零。 */
    public WeakValueLongCache(int concurrencyLevel, LongFunction<? extends V> createFunction) {
        super(concurrencyLevel, i -> new Segment<>());
        CacheReferenceQueue.add(this);
        this.createFunction = createFunction;
    }

    /** 返回现有缓存值。缺失时按该方法提供的计算函数生成，查找方法直接返回空值。 */
    public V getCache(final long k) {
        int mix = HashCommon.mix(Long.hashCode(k));
        return segmentFor(mix).getCache(k, mix, createFunction);
    }

    /** 在锁外执行计算，允许回调再次访问同一缓存。 */
    public V getCache(final long k, LongFunction<? extends V> createFunction) {
        int mix = HashCommon.mix(Long.hashCode(k));
        return segmentFor(mix).getCache(k, mix, createFunction);
    }

    /** 返回现有缓存值。缺失时按该方法提供的计算函数生成，查找方法直接返回空值。 */
    public V getIfPresent(final long k) {
        int mix = HashCommon.mix(Long.hashCode(k));
        return segmentFor(mix).getIfAbsent(k, mix);
    }

    /** 替换已被回收的节点，保留原键所在的链位置。 */
    public V putIfAbsent(final long k, final V v) {
        int mix = HashCommon.mix(Long.hashCode(k));
        return segmentFor(mix).put(k, v, mix);
    }

    /** 清除全部条目。 */
    public void clear() {
        clearSegments();
    }

    @Override
    public void clearCache() {
        expungeStaleEntries();
    }

    /** 替换已被回收的节点，保留原键所在的链位置。 */
    static final class Segment<V> extends WeakHashSegment<Node<V>> {

        private Segment() {}

        @Override
        @SuppressWarnings("unchecked")
        protected Node<V>[] newArray(int capacity) {
            return new Node[capacity];
        }

        @Override
        protected int nodeHash(Node<V> node) {
            return Long.hashCode(node.key);
        }

        /** 替换已被回收的节点，保留原键所在的链位置。 */
        private V getCache(final long k, final int mix, LongFunction<? extends V> createFunction) {
            long stamp = readLock();
            try {
                Node<V> curr = table[mix & mask];
                while (curr != null) {
                    if (curr.key == k) {
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
            final var v = createFunction.apply(k);
            stamp = writeLock();
            try {
                final int index = mix & mask;
                final Node<V> node = table[index];
                Node<V> prev = null;
                Node<V> curr = node;
                while (curr != null) {
                    if (curr.key == k) {
                        V existing = curr.get();
                        if (existing != null) {
                            return existing;
                        }
                        // 替换已被回收的节点，保留原键所在的链位置。
                        var n = new Node<>(k, v, curr.next, queue);
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
                table[index] = new Node<>(k, v, node, queue);
                if (++size > maxFill) {
                    resize();
                }
                return v;
            } finally {
                unlockWrite(stamp);
            }
        }

        /** 只读取现有条目。 */
        private V getIfAbsent(final long k, final int mix) {
            long stamp = readLock();
            try {
                Node<V> curr = table[mix & mask];
                while (curr != null) {
                    if (curr.key == k) {
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
        private V put(final long k, final V v, final int mix) {
            long stamp = writeLock();
            try {
                final int index = mix & mask;
                final Node<V> node = table[index];
                Node<V> prev = null;
                Node<V> curr = node;
                while (curr != null) {
                    if (curr.key == k) {
                        V old = curr.get();
                        if (old != null) return old;
                        // 替换已被回收的节点，保留原键所在的链位置。
                        var n = new Node<>(k, v, curr.next, queue);
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
                table[index] = new Node<>(k, v, node, queue);
                if (++size > maxFill) {
                    resize();
                }
                return v;
            } finally {
                unlockWrite(stamp);
            }
        }
    }

    static final class Node<V> extends WeakReference<V> implements ChainNode<Node<V>> {

        private final long key;
        private volatile Node<V> next;

        private Node(long key, V value, Node<V> next, ReferenceQueue<Object> queue) {
            super(value, queue);
            this.key = key;
            this.next = next;
        }

        @Override
        public Node<V> getNext() {
            return next;
        }

        @Override
        public void setNext(Node<V> next) {
            this.next = next;
        }
    }
}
