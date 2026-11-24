package com.modularmc.mmclutils.api.schedule;

import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;

/**
 * 可重复提交而在任一时刻只排队一次的任务。
 * 实例可跨线程提交和取消，但其动作始终在所属 {@link TickScheduler} 的驱动线程运行。
 */
public final class CoalescedTask {

    private static final VarHandle QUEUED;

    static {
        try {
            QUEUED = MethodHandles.lookup().findVarHandle(CoalescedTask.class, "queuedIn", TickScheduler.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final @Nullable Object owner;
    private final Runnable action;
    volatile TickScheduler.Entry entry;
    boolean lagging;
    @SuppressWarnings("unused")
    private volatile @Nullable TickScheduler queuedIn;

    /** 创建无所有者的合并任务。 */
    public CoalescedTask(Runnable action) {
        this(null, action);
    }

    /**
     * 创建带所有者的合并任务，供崩溃报告识别任务来源。
     *
     * @param owner 可选任务所有者
     * @param action 被调度的操作
     * @throws NullPointerException 操作为空
     */
    public CoalescedTask(@Nullable Object owner, Runnable action) {
        this.owner = owner;
        this.action = Objects.requireNonNull(action, "action");
        this.entry = TickScheduler.coalescedEntry(this, owner, action);
    }

    /** 查询此任务是否已进入尚未关闭的调度器队列。 */
    public boolean isQueued() {
        TickScheduler s = queuedIn();
        return s != null && !s.isClosed();
    }

    /** 取消当前排队的执行；之后仍可重新提交此任务。 */
    public void cancel() {
        TickScheduler s = queuedIn();
        if (s == null) return;
        TickScheduler.Entry old = entry;
        entry = TickScheduler.coalescedEntry(this, owner, action);
        old.cancel();
        QUEUED.compareAndSet(this, s, null);
    }

    void retire() {
        entry.cancel();
    }

    void submitTo(TickScheduler scheduler, int delay) {
        while (true) {
            TickScheduler current = queuedIn();
            if (current != null && !current.isClosed()) return;
            if (scheduler.isClosed()) return;
            if (QUEUED.compareAndSet(this, current, scheduler)) break;
        }
        scheduler.enqueueCoalesced(this, delay);
    }

    @Nullable
    TickScheduler queuedIn() {
        return (TickScheduler) QUEUED.getVolatile(this);
    }

    boolean dequeue(TickScheduler scheduler) {
        return QUEUED.compareAndSet(this, scheduler, null);
    }

    TickScheduler.Entry entryForLink(TickScheduler scheduler) {
        TickScheduler.Entry e = entry;
        if (e.linked || !e.active) {
            e = TickScheduler.coalescedEntry(this, owner, action);
            entry = e;
        }
        e.scheduler = scheduler;
        return e;
    }
}
