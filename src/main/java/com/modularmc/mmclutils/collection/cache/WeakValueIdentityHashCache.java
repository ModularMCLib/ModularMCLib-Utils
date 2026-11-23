package com.modularmc.mmclutils.collection.cache;

import it.unimi.dsi.fastutil.HashCommon;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** 按键保存弱引用值，无外部引用的值可由回收线程移除。 计算函数在锁外运行，并发写入在分段锁内合并。 */
public final class WeakValueIdentityHashCache<K, V> extends WeakSegmentedCache<WeakValueIdentityHashCache.Segment<K, V>> implements LoadingCache<K, V>, ReclaimableCache {

    private final Function<? super K, ? extends V> createFunction;

    /** 使用默认并发度。创建函数由参数决定，可省略。 */
    public WeakValueIdentityHashCache() {
        this(SegmentedCache.DEFAULT_CONCURRENCY, null);
    }

    /** 使用默认并发度。创建函数由参数决定，可省略。 */
    public WeakValueIdentityHashCache(Function<? super K, ? extends V> createFunction) {
        this(SegmentedCache.DEFAULT_CONCURRENCY, createFunction);
    }

    /** 按给定的并发度初始化缓存。并发度必须大于零。 */
    public WeakValueIdentityHashCache(int concurrencyLevel) {
        this(concurrencyLevel, null);
    }

    /** 按给定的并发度初始化缓存。并发度必须大于零。 */
    public WeakValueIdentityHashCache(int concurrencyLevel, Function<? super K, ? extends V> createFunction) {
        super(concurrencyLevel, i -> new Segment<>());
        CacheReferenceQueue.add(this);
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

    @Override
    public void clearCache() {
        expungeStaleEntries();
    }

    /** 替换已被回收的节点，保留原键所在的链位置。 */
    final static class Segment<K, V> extends WeakHashSegment<Node<K, V>> {

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

        /** 替换已被回收的节点，保留原键所在的链位置。 */
        private V getCache(final K k, final int mix, Function<? super K, ? extends V> createFunction, UnaryOperator<K> keyMappingFunction) {
            long stamp = readLock();
            try {
                Node<K, V> curr = table[mix & mask];
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
            final var mapped = keyMappingFunction.apply(k);
            final var v = createFunction.apply(mapped);
            stamp = writeLock();
            try {
                final int index = mix & mask;
                final Node<K, V> node = table[index];
                Node<K, V> prev = null;
                Node<K, V> curr = node;
                while (curr != null) {
                    if (curr.key == mapped) {
                        V existing = curr.get();
                        if (existing != null) {
                            return existing;
                        }
                        // 替换已被回收的节点，保留原键所在的链位置。
                        var n = new Node<>(mapped, v, curr.next, queue);
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
                table[index] = new Node<>(mapped, v, node, queue);
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
        private V put(final K k, final V v, final int mix) {
            long stamp = writeLock();
            try {
                final int index = mix & mask;
                final Node<K, V> node = table[index];
                Node<K, V> prev = null;
                Node<K, V> curr = node;
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

    static final class Node<K, V> extends WeakReference<V> implements ChainNode<Node<K, V>> {

        private final K key;
        private volatile Node<K, V> next;

        private Node(K key, V value, Node<K, V> next, ReferenceQueue<Object> queue) {
            super(value, queue);
            this.key = key;
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
