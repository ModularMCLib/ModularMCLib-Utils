package com.modularmc.mmclutils.api.collection.cache;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.HashCommon;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Arrays;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;

/** 记录弱引用节点并处理回收。命中的键可复用原有节点位置。 */
abstract class WeakHashSegment<N extends ChainNode<N>> extends HashSegment<N> {

    /** 替换已被回收的节点，保留原键所在的链位置。 */
    private static final int BACKGROUND_BATCH = 10000;

    protected final ReferenceQueue<Object> queue = new ReferenceQueue<>();

    protected WeakHashSegment() {
        int n = arraySize(Hash.DEFAULT_INITIAL_SIZE, Hash.DEFAULT_LOAD_FACTOR);
        this.table = newArray(n);
        this.mask = n - 1;
        this.maxFill = (int) (n * Hash.DEFAULT_LOAD_FACTOR);
    }

    /** 在写锁内合并并发变更。 */
    protected final void clear() {
        long stamp = writeLock();
        try {
            if (size != 0) {
                size = 0;
                Arrays.fill(table, null);
            }
            while (queue.poll() != null) {
                // 更新链表链接并保持探测顺序。
            }
        } finally {
            unlockWrite(stamp);
        }
    }

    /** 替换已被回收的节点，保留原键所在的链位置。 */
    @SuppressWarnings("unchecked")
    private int expungeLocked(int limit) {
        int processed = 0;
        Reference<?> ref;
        while (processed < limit && (ref = queue.poll()) != null) {
            unlink((N) ref);
            processed++;
        }
        return processed;
    }

    /** 替换已被回收的节点，保留原键所在的链位置。 */
    @SuppressWarnings("unchecked")
    final void expunge() {
        int processed = BACKGROUND_BATCH;
        while (processed == BACKGROUND_BATCH) {
            Reference<?> first = queue.poll();
            if (first == null) {
                return;
            }
            long stamp = writeLock();
            try {
                unlink((N) first);
                processed = 1 + expungeLocked(BACKGROUND_BATCH - 1);
            } finally {
                unlockWrite(stamp);
            }
        }
    }

    /** 在写锁内合并并发变更。 */
    final void expungeAll() {
        long stamp = writeLock();
        try {
            expungeLocked(Integer.MAX_VALUE);
        } finally {
            unlockWrite(stamp);
        }
    }

    /** 替换已被回收的节点，保留原键所在的链位置。 */
    private void unlink(N node) {
        int index = HashCommon.mix(nodeHash(node)) & mask;
        N prev = null;
        N curr = table[index];
        while (curr != null) {
            if (curr == node) {
                if (prev == null) {
                    table[index] = curr.getNext();
                } else {
                    prev.setNext(curr.getNext());
                }
                size--;
                return;
            }
            prev = curr;
            curr = curr.getNext();
        }
    }
}
