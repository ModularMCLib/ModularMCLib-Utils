package com.modularmc.mmclutils.api.collection.cache;

import it.unimi.dsi.fastutil.HashCommon;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.function.IntFunction;

/** 按键保存弱引用值，无外部引用的值可由回收线程移除。 计算函数在锁外运行，并发写入在分段锁内合并。 */
public final class WeakValueIntCache<V> extends WeakSegmentedCache<WeakValueIntCache.Segment<V>> implements ReclaimableCache {

    private final IntFunction<? extends V> createFunction;

    /** 使用默认并发度。创建函数由参数决定，可省略。 */
    public WeakValueIntCache() {
        this(SegmentedCache.DEFAULT_CONCURRENCY, null);
    }

    /** 使用默认并发度。创建函数由参数决定，可省略。 */
    public WeakValueIntCache(IntFunction<? extends V> createFunction) {
        this(SegmentedCache.DEFAULT_CONCURRENCY, createFunction);
    }

    /** 按给定的并发度初始化缓存。并发度必须大于零。 */
    public WeakValueIntCache(int concurrencyLevel) {
        this(concurrencyLevel, null);
    }

    /** 按给定的并发度初始化缓存。并发度必须大于零。 */
    public WeakValueIntCache(int concurrencyLevel, IntFunction<? extends V> createFunction) {
        super(concurrencyLevel, i -> new Segment<>());
        CacheReferenceQueue.add(this);
        this.createFunction = createFunction;
    }

    /** 返回现有缓存值。缺失时按该方法提供的计算函数生成，查找方法直接返回空值。 */
    public V getCache(final int k) {
        int mix = HashCommon.mix(k);
        return segmentFor(mix).getCache(k, mix, createFunction);
    }

    /** 在锁外执行计算，允许回调再次访问同一缓存。 */
    public V getCache(final int k, IntFunction<? extends V> createFunction) {
        int mix = HashCommon.mix(k);
        return segmentFor(mix).getCache(k, mix, createFunction);
    }

    /** 返回现有缓存值。缺失时按该方法提供的计算函数生成，查找方法直接返回空值。 */
    public V getIfPresent(final int k) {
        int mix = HashCommon.mix(k);
        return segmentFor(mix).getIfAbsent(k, mix);
    }

    /** 替换已被回收的节点，保留原键所在的链位置。 */
    public V putIfAbsent(final int k, final V v) {
        int mix = HashCommon.mix(k);
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
            return node.key;
        }

        /** 替换已被回收的节点，保留原键所在的链位置。 */
        private V getCache(final int k, final int mix, IntFunction<? extends V> createFunction) {
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
        private V getIfAbsent(final int k, final int mix) {
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
        private V put(final int k, final V v, final int mix) {
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

        private final int key;
        private volatile Node<V> next;

        private Node(int key, V value, Node<V> next, ReferenceQueue<Object> queue) {
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
