package com.modularmc.mmclutils.api.collection.cache;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.HashCommon;

import java.util.function.UnaryOperator;

/**
 * 按调用方提供的哈希与相等策略驻留对象。驻留条目由该实例强引用。
 *
 * @param <T> 驻留对象类型
 */
public final class CustomHashInterner<T> extends SegmentedCache<CustomHashInterner.Segment<T>> implements Interner<T> {

    private final Hash.Strategy<? super T> strategy;

    /** 使用默认并发度。创建函数由参数决定，可省略。 */
    public CustomHashInterner(Hash.Strategy<? super T> strategy) {
        this(SegmentedCache.DEFAULT_CONCURRENCY, strategy);
    }

    /** 按给定的并发度初始化缓存。并发度必须大于零。 */
    public CustomHashInterner(int concurrencyLevel, Hash.Strategy<? super T> strategy) {
        super(concurrencyLevel, i -> new Segment<>(strategy));
        this.strategy = strategy;
    }

    @Override
    public T intern(final T sample) {
        int hash = strategy.hashCode(sample);
        int mix = HashCommon.mix(hash);
        return segmentFor(mix).intern(sample, hash, mix, Interner.identityMappingFunction());
    }

    @Override
    public T intern(T sample, UnaryOperator<T> mappingFunction) {
        int hash = strategy.hashCode(sample);
        int mix = HashCommon.mix(hash);
        return segmentFor(mix).intern(sample, hash, mix, mappingFunction);
    }

    @Override
    public boolean isPresent(final T sample) {
        int hash = strategy.hashCode(sample);
        int mix = HashCommon.mix(hash);
        return segmentFor(mix).contains(sample, hash, mix);
    }

    @Override
    public boolean addIfAbsent(final T sample) {
        int hash = strategy.hashCode(sample);
        int mix = HashCommon.mix(hash);
        return segmentFor(mix).add(sample, hash, mix);
    }

    @Override
    public void clear() {
        clearSegments();
    }

    final static class Segment<T> extends HashSegment<Node<T>> {

        private final Hash.Strategy<? super T> strategy;

        private Segment(Hash.Strategy<? super T> strategy) {
            this.strategy = strategy;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected Node<T>[] newArray(int capacity) {
            return new Node[capacity];
        }

        @Override
        protected int nodeHash(Node<T> node) {
            return node.hash;
        }

        /** 在写锁内合并并发变更。 */
        private T intern(final T k, final int hash, int mix, UnaryOperator<T> mappingFunction) {
            long stamp = readLock();
            try {
                Node<T> curr = table[mix & mask];
                while (curr != null) {
                    var key = curr.key;
                    if (curr.hash == hash && (key == k || strategy.equals(k, key))) {
                        return key;
                    }
                    curr = curr.next;
                }
            } finally {
                unlockRead(stamp);
            }
            final T v = mappingFunction.apply(k);
            stamp = writeLock();
            try {
                final int index = mix & mask;
                final Node<T> node = table[index];
                Node<T> curr = node;
                while (curr != null) {
                    var key = curr.key;
                    if (curr.hash == hash && (key == v || strategy.equals(v, key))) {
                        return key;
                    }
                    curr = curr.next;
                }
                table[index] = new Node<>(v, hash, node);
                if (++size > maxFill) {
                    resize();
                }
                return v;
            } finally {
                unlockWrite(stamp);
            }
        }

        private boolean contains(final T k, final int hash, int mix) {
            long stamp = readLock();
            try {
                final int index = mix & mask;
                Node<T> curr = table[index];
                while (curr != null) {
                    if (curr.hash == hash && (curr.key == k || strategy.equals(k, curr.key))) {
                        return true;
                    }
                    curr = curr.next;
                }
                return false;
            } finally {
                unlockRead(stamp);
            }
        }

        /** 只在缺少该键时写入。 */
        private boolean add(final T k, final int hash, int mix) {
            long stamp = writeLock();
            try {
                final int index = mix & mask;
                final Node<T> node = table[index];
                Node<T> curr = node;
                while (curr != null) {
                    if (curr.hash == hash && (curr.key == k || strategy.equals(k, curr.key))) {
                        return false;
                    }
                    curr = curr.next;
                }
                table[index] = new Node<>(k, hash, node);
                if (++size > maxFill) {
                    resize();
                }
                return true;
            } finally {
                unlockWrite(stamp);
            }
        }
    }

    static final class Node<T> implements ChainNode<Node<T>> {

        private final T key;
        private final int hash;
        private volatile Node<T> next;

        private Node(T key, int hash, Node<T> next) {
            this.key = key;
            this.hash = hash;
            this.next = next;
        }

        @Override
        public Node<T> getNext() {
            return next;
        }

        @Override
        public void setNext(Node<T> next) {
            this.next = next;
        }
    }
}
