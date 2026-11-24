package com.modularmc.mmclutils.api.schedule;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2LongLinkedOpenHashMap;

/** 由所属 Tick 线程维护的任务队列。按引用去重，到期任务按登记顺序取出。 */
public final class PriorityTaskQueue<T> {

    private final Reference2LongLinkedOpenHashMap<T> dueTicks = new Reference2LongLinkedOpenHashMap<>();
    private long firstDueTick = Long.MAX_VALUE;

    /** 按引用登记任务。再次登记同一个对象会更新时间，保持队列中的原顺序。 */
    public void schedule(T task, long dueTick) {
        dueTicks.put(task, dueTick);
        firstDueTick = Math.min(firstDueTick, dueTick);
    }

    /** 调整已有任务时间或将新任务放到队首。 */
    public void scheduleFirst(T task, long dueTick) {
        dueTicks.putAndMoveToFirst(task, dueTick);
        firstDueTick = Math.min(firstDueTick, dueTick);
    }

    /** 从队列移除给定的任务对象。返回是否存在该对象。 */
    public boolean cancel(T task) {
        if (!dueTicks.containsKey(task)) return false;
        dueTicks.removeLong(task);
        return true;
    }

    /** 返回尚未取出或取消的任务个数。 */
    public int size() {
        return dueTicks.size();
    }

    /** 取出截至指定轮次已到期的全部任务，结果按队列顺序排列。 */
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

    /** 丢弃排队的全部任务并重置最早到期时间。 */
    public void clear() {
        dueTicks.clear();
        firstDueTick = Long.MAX_VALUE;
    }
}
