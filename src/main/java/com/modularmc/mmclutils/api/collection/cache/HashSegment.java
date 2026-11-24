package com.modularmc.mmclutils.api.collection.cache;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.HashCommon;

import java.util.Arrays;
import java.util.concurrent.locks.StampedLock;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;

/** 分段缓存中的拉链哈希表，读写由此分段自己的锁保护。 */
abstract class HashSegment<N extends ChainNode<N>> extends StampedLock {

    protected volatile N[] table;
    protected volatile int mask;
    protected volatile int size;
    protected volatile int maxFill;

    protected HashSegment() {
        int n = arraySize(Hash.DEFAULT_INITIAL_SIZE, Hash.DEFAULT_LOAD_FACTOR);
        this.table = newArray(n);
        this.mask = n - 1;
        this.maxFill = (int) (n * Hash.DEFAULT_LOAD_FACTOR);
    }

    protected abstract int nodeHash(N node);

    protected abstract N[] newArray(int capacity);

    /** 在写锁内合并并发变更。 */
    protected final void resize() {
        N[] oldTab = this.table;
        int oldCap = oldTab.length;
        int newCap = oldCap << 1;
        N[] newTab = newArray(newCap);
        int newMask = newCap - 1;
        for (int i = 0; i < oldCap; ++i) {
            N e;
            if ((e = oldTab[i]) != null) {
                oldTab[i] = null;
                if (e.getNext() == null) {
                    newTab[HashCommon.mix(nodeHash(e)) & newMask] = e;
                } else {
                    N loHead = null, loTail = null;
                    N hiHead = null, hiTail = null;
                    N next;
                    do {
                        next = e.getNext();
                        if ((HashCommon.mix(nodeHash(e)) & oldCap) == 0) {
                            if (loTail == null)
                                loHead = e;
                            else
                                loTail.setNext(e);
                            loTail = e;
                        } else {
                            if (hiTail == null)
                                hiHead = e;
                            else
                                hiTail.setNext(e);
                            hiTail = e;
                        }
                    } while ((e = next) != null);
                    if (loTail != null) {
                        loTail.setNext(null);
                        newTab[HashCommon.mix(nodeHash(loHead)) & newMask] = loHead;
                    }
                    if (hiTail != null) {
                        hiTail.setNext(null);
                        newTab[HashCommon.mix(nodeHash(hiHead)) & newMask] = hiHead;
                    }
                }
            }
        }
        this.table = newTab;
        this.mask = newMask;
        this.maxFill = (int) (newCap * Hash.DEFAULT_LOAD_FACTOR);
    }

    /** 在写锁内合并并发变更。 */
    protected void clear() {
        long stamp = writeLock();
        try {
            if (size != 0) {
                size = 0;
                Arrays.fill(table, null);
            }
        } finally {
            unlockWrite(stamp);
        }
    }
}
