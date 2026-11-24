package com.modularmc.mmclutils.api.collection.cache;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.floats.Float2ObjectFunction;

/**
 * 使用原始类型 {@code float} 作为键，强引用缓存值，直到缓存条目被清除。
 * 计算函数在分段锁外运行，并发写入在锁内合并。
 * 浮点键区分正负零，同一类型的 NaN 使用相同的键。
 *
 * @param <V> 缓存值类型
 */
public final class FloatCache<V> extends SegmentedCache<FloatCache.Segment<V>> {

    private final Float2ObjectFunction<? extends V> createFunction;

    /** 使用默认并发度。创建函数由参数决定，可省略。 */
    public FloatCache() {
        this(SegmentedCache.DEFAULT_CONCURRENCY, null);
    }

    /** 使用默认并发度。创建函数由参数决定，可省略。 */
    public FloatCache(Float2ObjectFunction<? extends V> createFunction) {
        this(SegmentedCache.DEFAULT_CONCURRENCY, createFunction);
    }

    /** 按给定的并发度初始化缓存。并发度必须大于零。 */
    public FloatCache(int concurrencyLevel) {
        this(concurrencyLevel, null);
    }

    /** 按给定的并发度初始化缓存。并发度必须大于零。 */
    public FloatCache(int concurrencyLevel, Float2ObjectFunction<? extends V> createFunction) {
        super(concurrencyLevel, i -> new Segment<>());
        this.createFunction = createFunction;
    }

    /** 返回现有缓存值。缺失时按该方法提供的计算函数生成，查找方法直接返回空值。 */
    public V getCache(final float k) {
        int mix = HashCommon.mix(Float.floatToIntBits(k));
        return segmentFor(mix).getCache(k, mix, createFunction);
    }

    /** 在锁外执行计算，允许回调再次访问同一缓存。 */
    public V getCache(final float k, Float2ObjectFunction<? extends V> createFunction) {
        int mix = HashCommon.mix(Float.floatToIntBits(k));
        return segmentFor(mix).getCache(k, mix, createFunction);
    }

    /** 返回现有缓存值。缺失时按该方法提供的计算函数生成，查找方法直接返回空值。 */
    public V getIfPresent(final float k) {
        int mix = HashCommon.mix(Float.floatToIntBits(k));
        return segmentFor(mix).getIfAbsent(k, mix);
    }

    /** 只在没有有效值时写入，返回最终关联的值。 */
    public V putIfAbsent(final float k, final V v) {
        int mix = HashCommon.mix(Float.floatToIntBits(k));
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
            return Float.floatToIntBits(node.key);
        }

        /** 在写锁内合并并发变更。 */
        private V getCache(final float k, final int mix, Float2ObjectFunction<? extends V> createFunction) {
            long stamp = readLock();
            try {
                Node<V> curr = table[mix & mask];
                while (curr != null) {
                    if (Float.floatToIntBits(curr.key) == Float.floatToIntBits(k)) {
                        return curr.value;
                    }
                    curr = curr.next;
                }
            } finally {
                unlockRead(stamp);
            }
            // 在锁外执行计算，允许回调再次访问同一缓存。
            final var v = createFunction.get(k);
            stamp = writeLock();
            try {
                final int index = mix & mask;
                Node<V> curr = table[index];
                while (curr != null) {
                    if (Float.floatToIntBits(curr.key) == Float.floatToIntBits(k)) {
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
        private V getIfAbsent(final float k, final int mix) {
            long stamp = readLock();
            try {
                Node<V> curr = table[mix & mask];
                while (curr != null) {
                    if (Float.floatToIntBits(curr.key) == Float.floatToIntBits(k)) {
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
        private V put(final float k, final V v, final int mix) {
            long stamp = writeLock();
            try {
                final int index = mix & mask;
                final Node<V> node = table[index];
                Node<V> curr = node;
                while (curr != null) {
                    if (Float.floatToIntBits(curr.key) == Float.floatToIntBits(k)) {
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

        private final float key;
        private final V value;
        private volatile Node<V> next;

        private Node(float key, V value, Node<V> next) {
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
