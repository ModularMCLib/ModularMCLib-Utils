package com.modularmc.mmclutils.collection.cache;

import java.util.function.IntFunction;

/** 以二的幂次分段索引，多个线程可并行访问不同分段。 */
abstract class SegmentedCache<SEG extends HashSegment<?>> {

    static final int DEFAULT_CONCURRENCY = Runtime.getRuntime().availableProcessors();

    protected final SEG[] segments;
    private final int segmentShift;
    private final int segmentMask;

    /** 并发度必须大于零。 */
    @SuppressWarnings("unchecked")
    protected SegmentedCache(int concurrencyLevel, IntFunction<? extends SEG> segmentFactory) {
        if (concurrencyLevel <= 0) throw new IllegalArgumentException("concurrencyLevel must be positive");
        int ssize = 1;
        while (ssize < concurrencyLevel) {
            ssize <<= 1;
        }
        this.segmentShift = 32 - Integer.numberOfTrailingZeros(ssize);
        this.segmentMask = ssize - 1;
        this.segments = (SEG[]) new HashSegment[ssize];
        for (int i = 0; i < ssize; i++) {
            segments[i] = segmentFactory.apply(i);
        }
    }

    protected final SEG segmentFor(int mix) {
        return segments[(mix >>> segmentShift) & segmentMask];
    }

    protected final void clearSegments() {
        for (var segment : segments) {
            segment.clear();
        }
    }

    public final int size() {
        int total = 0;
        for (var segment : segments) {
            total += segment.size;
        }
        return total;
    }
}
