package com.modularmc.mmclutils.collection.cache;

import it.unimi.dsi.fastutil.HashCommon;

import java.util.function.DoubleFunction;

/** 按键保存可复用的值。 计算函数在锁外运行，并发写入在分段锁内合并。 */
public final class DoubleCache<V> extends SegmentedCache<DoubleCache.Segment<V>> {

    private final DoubleFunction<? extends V> createFunction;

    /** 使用默认并发度。创建函数由参数决定，可省略。 */
    public DoubleCache() {
        this(SegmentedCache.DEFAULT_CONCURRENCY, null);
    }

    /** 使用默认并发度。创建函数由参数决定，可省略。 */
    public DoubleCache(DoubleFunction<? extends V> createFunction) {
        this(SegmentedCache.DEFAULT_CONCURRENCY, createFunction);
    }

    /** 按给定的并发度初始化缓存。并发度必须大于零。 */
    public DoubleCache(int concurrencyLevel) {
        this(concurrencyLevel, null);
    }

    /** 按给定的并发度初始化缓存。并发度必须大于零。 */
    public DoubleCache(int concurrencyLevel, DoubleFunction<? extends V> createFunction) {
        super(concurrencyLevel, i -> new Segment<>());
        this.createFunction = createFunction;
    }

    /** 返回现有缓存值。缺失时按该方法提供的计算函数生成，查找方法直接返回空值。 */
    public V getCache(final double k) {
        int mix = HashCommon.mix(Long.hashCode(Double.doubleToLongBits(k)));
        return segmentFor(mix).getCache(k, mix, createFunction);
    }

    /** 在锁外执行计算，允许回调再次访问同一缓存。 */
    public V getCache(final double k, DoubleFunction<? extends V> createFunction) {
        int mix = HashCommon.mix(Long.hashCode(Double.doubleToLongBits(k)));
        return segmentFor(mix).getCache(k, mix, createFunction);
    }

    /** 返回现有缓存值。缺失时按该方法提供的计算函数生成，查找方法直接返回空值。 */
    public V getIfPresent(final double k) {
        int mix = HashCommon.mix(Long.hashCode(Double.doubleToLongBits(k)));
        return segmentFor(mix).getIfAbsent(k, mix);
    }

    /** 只在没有有效值时写入，返回最终关联的值。 */
    public V putIfAbsent(final double k, final V v) {
        int mix = HashCommon.mix(Long.hashCode(Double.doubleToLongBits(k)));
        return segmentFor(mix).put(k, v, mix);
    }

    /** 清除全部条目。 */
    public void clear() {
        clearSegments();
    }

    static final class Segment<V> extends HashSegment<Node<V>> {

        private Segment() {}

        @Override
        @SuppressWarnings("unchecked")
        protected Node<V>[] newArray(int capacity) {
            return new Node[capacity];
        }

        @Override
        protected int nodeHash(Node<V> node) {
            return Long.hashCode(Double.doubleToLongBits(node.key));
        }

        /** 在写锁内合并并发变更。 */
        private V getCache(final double k, final int mix, DoubleFunction<? extends V> createFunction) {
            long stamp = readLock();
            try {
                Node<V> curr = table[mix & mask];
                while (curr != null) {
                    if (Double.doubleToLongBits(curr.key) == Double.doubleToLongBits(k)) {
                        return curr.value;
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
                Node<V> curr = table[index];
                while (curr != null) {
                    if (Double.doubleToLongBits(curr.key) == Double.doubleToLongBits(k)) {
                        return curr.value;
                    }
                    curr = curr.next;
                }
                table[index] = new Node<>(k, v, table[index]);
                if (++size > maxFill) {
                    resize();
                }
                return v;
            } finally {
                unlockWrite(stamp);
            }
        }

        /** 只读取现有条目。 */
        private V getIfAbsent(final double k, final int mix) {
            long stamp = readLock();
            try {
                Node<V> curr = table[mix & mask];
                while (curr != null) {
                    if (Double.doubleToLongBits(curr.key) == Double.doubleToLongBits(k)) {
                        return curr.value;
                    }
                    curr = curr.next;
                }
                return null;
            } finally {
                unlockRead(stamp);
            }
        }

        /** 只在缺少该键时写入。 */
        private V put(final double k, final V v, final int mix) {
            long stamp = writeLock();
            try {
                final int index = mix & mask;
                final Node<V> node = table[index];
                Node<V> curr = node;
                while (curr != null) {
                    if (Double.doubleToLongBits(curr.key) == Double.doubleToLongBits(k)) {
                        return curr.value;
                    }
                    curr = curr.next;
                }
                table[index] = new Node<>(k, v, node);
                if (++size > maxFill) {
                    resize();
                }
                return v;
            } finally {
                unlockWrite(stamp);
            }
        }
    }

    static final class Node<V> implements ChainNode<Node<V>> {

        private final double key;
        private final V value;
        private volatile Node<V> next;

        private Node(double key, V value, Node<V> next) {
            this.key = key;
            this.value = value;
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
