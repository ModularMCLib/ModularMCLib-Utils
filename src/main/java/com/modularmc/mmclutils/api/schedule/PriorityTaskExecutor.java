package com.modularmc.mmclutils.api.schedule;

import java.util.concurrent.Executor;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** 单线程执行异步计算。常规任务按登记顺序运行，优先任务可插入队首。 */
public final class PriorityTaskExecutor implements Executor, AutoCloseable {

    private final AtomicLong order = new AtomicLong();
    private final ThreadPoolExecutor executor;

    /** 启动使用指定线程名的串行执行器。首个任务提交时才会创建工作线程。 */
    public PriorityTaskExecutor(String threadName) {
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new PriorityBlockingQueue<>(), Thread.ofPlatform().daemon().name(threadName).factory());
    }

    @Override
    public void execute(Runnable action) {
        executor.execute(new Calculation(order.getAndIncrement(), action));
    }

    /** 返回一次性优先入口。首次提交进入优先队列，后续提交进入常规队列。 */
    public Executor firstExecutor() {
        long priority = -order.getAndIncrement() - 1;
        var first = new AtomicBoolean(true);
        return action -> {
            if (first.getAndSet(false)) executor.execute(new Calculation(priority, action));
            else execute(action);
        };
    }

    /** 停止接收任务，取消队列并尝试中断运行中的任务。 */
    @Override
    public void close() {
        executor.shutdownNow();
    }

    private record Calculation(long order, Runnable action) implements Runnable, Comparable<Calculation> {

        @Override
        public int compareTo(Calculation other) {
            return Long.compare(order, other.order);
        }

        @Override
        public void run() {
            action.run();
        }
    }
}
