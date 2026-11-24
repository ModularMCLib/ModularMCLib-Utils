package com.modularmc.mmclutils.api.collection.cache;

import java.util.function.IntFunction;

/** 在分段缓存上统一分发弱引用回收通知。 */
abstract class WeakSegmentedCache<SEG extends WeakHashSegment<?>> extends SegmentedCache<SEG> {

    /** 并发度必须大于零。 */
    protected WeakSegmentedCache(int concurrencyLevel, IntFunction<? extends SEG> segmentFactory) {
        super(concurrencyLevel, segmentFactory);
    }

    /** 在写锁内合并并发变更。 */
    protected final void expungeStaleEntries() {
        HashSegment[] segments = this.segments;
        for (var segment : segments) {
            ((WeakHashSegment<?>) segment).expungeAll();
        }
    }

    /** 替换已被回收的节点，保留原键所在的链位置。 */
    final void drainCollected() {
        HashSegment[] segments = this.segments;
        for (var segment : segments) {
            ((WeakHashSegment<?>) segment).expunge();
        }
    }
}
