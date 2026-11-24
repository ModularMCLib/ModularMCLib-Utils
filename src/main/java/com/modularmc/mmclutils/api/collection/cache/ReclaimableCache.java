package com.modularmc.mmclutils.api.collection.cache;

/** 弱引用缓存向清理线程提供的回收入口。 */
interface ReclaimableCache {

    void clearCache();
}
