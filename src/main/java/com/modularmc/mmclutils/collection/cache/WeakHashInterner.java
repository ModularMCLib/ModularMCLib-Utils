package com.modularmc.mmclutils.collection.cache;

import it.unimi.dsi.fastutil.HashCommon;

import java.util.function.UnaryOperator;

/** 按相等策略驻留或查找对象。无强引用的条目可以回收。 */
public final class WeakHashInterner<T> extends WeakSegmentedCache<WeakHashInterner.Segment<T>> implements Interner<T>, ReclaimableCache {

    /** 使用默认并发度。创建函数由参数决定，可省略。 */
    public WeakHashInterner() {
        this(SegmentedCache.DEFAULT_CONCURRENCY);
    }

    /** 按给定的并发度初始化缓存。并发度必须大于零。 */
    public WeakHashInterner(int concurrencyLevel) {
        super(concurrencyLevel, i -> new Segment<>());
        CacheReferenceQueue.add(this);
    }

    @Override
    public T intern(final T sample) {
        int hash = sample.hashCode();
        int mix = HashCommon.mix(hash);
        return segmentFor(mix).intern(sample, hash, mix, Interner.identityMappingFunction());
    }

    @Override
    public T intern(T sample, UnaryOperator<T> mappingFunction) {
        int hash = sample.hashCode();
        int mix = HashCommon.mix(hash);
        return segmentFor(mix).intern(sample, hash, mix, mappingFunction);
    }

    @Override
    public boolean isPresent(final T sample) {
        int hash = sample.hashCode();
        int mix = HashCommon.mix(hash);
        return segmentFor(mix).contains(sample, hash, mix);
    }

    @Override
    public boolean addIfAbsent(final T sample) {
        int hash = sample.hashCode();
        int mix = HashCommon.mix(hash);
        return segmentFor(mix).add(sample, hash, mix);
    }

    @Override
    public void clear() {
        clearSegments();
    }

    @Override
    public void clearCache() {
        expungeStaleEntries();
    }

    final static class Segment<T> extends WeakHashSegment<WeakReferenceNode<T>> {

        private Segment() {}

        @Override
        @SuppressWarnings("unchecked")
        protected WeakReferenceNode<T>[] newArray(int capacity) {
            return new WeakReferenceNode[capacity];
        }

        @Override
        protected int nodeHash(WeakReferenceNode<T> node) {
            return node.hash;
        }

        /** 在写锁内合并并发变更。 */
        private T intern(final T k, final int hash, int mix, UnaryOperator<T> mappingFunction) {
            long stamp = readLock();
            try {
                WeakReferenceNode<T> curr = table[mix & mask];
                while (curr != null) {
                    T key = curr.get();
                    if (key != null && curr.hash == hash && (key == k || k.equals(key))) {
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
                WeakReferenceNode<T> node = table[index];
                WeakReferenceNode<T> prev = null;
                WeakReferenceNode<T> curr = node;
                while (curr != null) {
                    T key = curr.get();
                    if (key == null) {
                        if (prev == null) {
                            node = curr.next;
                            table[index] = node;
                        } else {
                            prev.next = curr.next;
                        }
                        size--;
                    } else {
                        if (curr.hash == hash && (key == v || v.equals(key))) {
                            return key;
                        }
                        prev = curr;
                    }
                    curr = curr.next;
                }
                table[index] = new WeakReferenceNode<>(v, hash, node, queue);
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
                WeakReferenceNode<T> curr = table[index];
                while (curr != null) {
                    T key = curr.get();
                    // 替换已被回收的节点，保留原键所在的链位置。

                    if (key != null && curr.hash == hash && (key == k || k.equals(key))) {
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
                WeakReferenceNode<T> node = table[index];
                WeakReferenceNode<T> prev = null;
                WeakReferenceNode<T> curr = node;
                while (curr != null) {
                    T key = curr.get();
                    if (key == null) {
                        if (prev == null) {
                            node = curr.next;
                            table[index] = node;
                        } else {
                            prev.next = curr.next;
                        }
                        size--;
                    } else {
                        if (curr.hash == hash && (key == k || k.equals(key))) {
                            return false;
                        }
                        prev = curr;
                    }
                    curr = curr.next;
                }
                table[index] = new WeakReferenceNode<>(k, hash, node, queue);
                if (++size > maxFill) {
                    resize();
                }
                return true;
            } finally {
                unlockWrite(stamp);
            }
        }
    }
}
