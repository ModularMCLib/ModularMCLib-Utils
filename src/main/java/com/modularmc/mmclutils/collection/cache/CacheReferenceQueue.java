package com.modularmc.mmclutils.collection.cache;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentLinkedDeque;

/** 集中清理由引用队列报告的失效项，避免为每个弱引用缓存启动线程。 */
final class CacheReferenceQueue {

    private CacheReferenceQueue() {}

    private static final long INTERVAL_MS = 500L;

    private static final ConcurrentLinkedDeque<WeakReference<WeakSegmentedCache<?>>> CACHES = new ConcurrentLinkedDeque<>();

    static {
        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(INTERVAL_MS);
                } catch (InterruptedException e) {
                    return;
                }
                sweep();
            }
        }, "FastCollection Cache Drainer");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    public static void add(WeakSegmentedCache<?> cache) {
        CACHES.offerLast(new WeakReference<>(cache));
    }

    private static void sweep() {
        var it = CACHES.iterator();
        while (it.hasNext()) {
            WeakSegmentedCache<?> cache = it.next().get();
            if (cache == null) {
                it.remove();
            } else {
                cache.drainCollected();
            }
        }
    }
}
