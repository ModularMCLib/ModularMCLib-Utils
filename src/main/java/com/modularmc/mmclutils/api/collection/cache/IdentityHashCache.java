package com.modularmc.mmclutils.api.collection.cache;

import it.unimi.dsi.fastutil.HashCommon;

import java.util.function.Function;
import java.util.function.UnaryOperator;

/** 按键保存可复用的值。 计算函数在锁外运行，并发写入在分段锁内合并。 */
public final class IdentityHashCache<K, V> extends SegmentedCache<IdentityHashCache.Segment<K, V>> implements LoadingCache<K, V> {

    private final Function<? super K, ? extends V> createFunction;

    /** 使用默认并发度。创建函数由参数决定，可省略。 */
    public IdentityHashCache() {
        this(SegmentedCache.DEFAULT_CONCURRENCY, null);
    }

    /** 使用默认并发度。创建函数由参数决定，可省略。 */
    public IdentityHashCache(Function<? super K, ? extends V> createFunction) {
        this(SegmentedCache.DEFAULT_CONCURRENCY, createFunction);
    }

    /** 按给定的并发度初始化缓存。并发度必须大于零。 */
    public IdentityHashCache(int concurrencyLevel) {
        this(concurrencyLevel, null);
    }

    /** 按给定的并发度初始化缓存。并发度必须大于零。 */
    public IdentityHashCache(int concurrencyLevel, Function<? super K, ? extends V> createFunction) {
        super(concurrencyLevel, i -> new Segment<>());
        this.createFunction = createFunction;
    }

    @Override
    public Function<? super K, ? extends V> createFunction() {
        return createFunction;
    }

    @Override
    public V getCache(final K k, Function<? super K, ? extends V> createFunction) {
        int hash = System.identityHashCode(k);
        int mix = HashCommon.mix(hash);
        return segmentFor(mix).getCache(k, mix, createFunction, Interner.identityMappingFunction());
    }

    @Override
    public V getCache(K k, Function<? super K, ? extends V> createFunction, UnaryOperator<K> keyMappingFunction) {
        int hash = System.identityHashCode(k);
        int mix = HashCommon.mix(hash);
        return segmentFor(mix).getCache(k, mix, createFunction, keyMappingFunction);
    }

    @Override
    public V getCacheRecursive(final K k, Function<? super K, ? extends V> createFunction) {
        int hash = System.identityHashCode(k);
        int mix = HashCommon.mix(hash);
        return segmentFor(mix).getCache(k, mix, createFunction, Interner.identityMappingFunction());
    }

    @Override
    public V getCacheRecursive(final K k, Function<? super K, ? extends V> createFunction, UnaryOperator<K> keyMappingFunction) {
        int hash = System.identityHashCode(k);
        int mix = HashCommon.mix(hash);
        return segmentFor(mix).getCache(k, mix, createFunction, keyMappingFunction);
    }

    @Override
    public V getIfPresent(final K k) {
        int hash = System.identityHashCode(k);
        int mix = HashCommon.mix(hash);
        return segmentFor(mix).getIfAbsent(k, mix);
    }

    @Override
    public V putIfAbsent(final K k, final V v) {
        int hash = System.identityHashCode(k);
        int mix = HashCommon.mix(hash);
        return segmentFor(mix).put(k, v, mix);
    }

    @Override
    public void clear() {
        clearSegments();
    }

    final static class Segment<K, V> extends HashSegment<Node<K, V>> {

        private Segment() {}

        @Override
        @SuppressWarnings("unchecked")
        protected Node<K, V>[] newArray(int capacity) {
            return new Node[capacity];
        }

        @Override
        protected int nodeHash(Node<K, V> node) {
            return System.identityHashCode(node.key);
        }

        /** 在写锁内合并并发变更。 */
        private V getCache(final K k, final int mix, Function<? super K, ? extends V> createFunction, UnaryOperator<K> keyMappingFunction) {
            long stamp = readLock();
            try {
                Node<K, V> curr = table[mix & mask];
                while (curr != null) {
                    if (curr.key == k) {
                        return curr.value;
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
                Node<K, V> curr = table[index];
                while (curr != null) {
                    if (curr.key == mapped) {
                        return curr.value;
                    }
                    curr = curr.next;
                }
                table[index] = new Node<>(mapped, v, table[index]);
                if (++size > maxFill) {
                    resize();
                }
                return v;
            } finally {
                unlockWrite(stamp);
            }
        }

        /** 只读取现有条目。 */
        private V getIfAbsent(final K k, final int mix) {
            long stamp = readLock();
            try {
                final int index = mix & mask;
                Node<K, V> curr = table[index];
                while (curr != null) {
                    if (curr.key == k) {
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
        private V put(final K k, final V v, final int mix) {
            long stamp = writeLock();
            try {
                final int index = mix & mask;
                final Node<K, V> node = table[index];
                Node<K, V> curr = node;
                while (curr != null) {
                    if (curr.key == k) {
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

    static final class Node<K, V> implements ChainNode<Node<K, V>> {

        private final K key;
        private final V value;
        private volatile Node<K, V> next;

        private Node(K key, V value, Node<K, V> next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }

        @Override
        public Node<K, V> getNext() {
            return next;
        }

        @Override
        public void setNext(Node<K, V> next) {
            this.next = next;
        }
    }
}
