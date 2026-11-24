package com.modularmc.mmclutils.api.schedule;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2LongLinkedOpenHashMap;

/** 由所属 Tick 线程维护的任务队列。按引用去重，到期任务按登记顺序取出。 */
public final class PriorityTaskQueue<T> {

    private final Reference2LongLinkedOpenHashMap<T> dueTicks = new Reference2LongLinkedOpenHashMap<>();
    private long firstDueTick = Long.MAX_VALUE;

    public void schedule(T task, long dueTick) {
        dueTicks.put(task, dueTick);
        firstDueTick = Math.min(firstDueTick, dueTick);
    }

    /** 调整已有任务时间或将新任务放到队首。 */
    public void scheduleFirst(T task, long dueTick) {
        dueTicks.putAndMoveToFirst(task, dueTick);
        firstDueTick = Math.min(firstDueTick, dueTick);
    }

    public boolean cancel(T task) {
        if (!dueTicks.containsKey(task)) return false;
        dueTicks.removeLong(task);
        return true;
    }

    public int size() {
        return dueTicks.size();
    }

    public ObjectList<T> pollDue(long tick) {
        if (tick < firstDueTick || dueTicks.isEmpty()) return ObjectList.of();
        var ready = new ObjectArrayList<T>();
        firstDueTick = Long.MAX_VALUE;
        var entries = dueTicks.reference2LongEntrySet().fastIterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            long due = entry.getLongValue();
            if (due <= tick) {
                ready.add(entry.getKey());
                entries.remove();
            } else {
                firstDueTick = Math.min(firstDueTick, due);
            }
        }
        return ready;
    }

    public void clear() {
        dueTicks.clear();
        firstDueTick = Long.MAX_VALUE;
    }
}
