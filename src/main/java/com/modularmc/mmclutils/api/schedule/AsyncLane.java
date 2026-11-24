package com.modularmc.mmclutils.api.schedule;

import com.modularmc.mmclutils.api.level.LevelStore;

import net.minecraft.world.level.Level;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 将一个维度的调度轮次提交到指定 Executor，并在同一维度上跳过尚未完成的轮次。
 * 调度任务在传入的执行器线程运行，除提交线程安全的调度操作外，不应直接读取游戏线程状态。
 * 执行器由创建方管理，本类只在维度卸载时关闭该维度的调度器。
 */
public final class AsyncLane {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile AsyncLane[] lanes = new AsyncLane[0];

    /** 使用库默认后台线程池的异步车道。 */
    public static final AsyncLane DEFAULT = create("mmclutils:default", defaultExecutor());

    private final String id;
    private final Executor executor;
    private final LevelStore<Runner> runners;

    private AsyncLane(String id, Executor executor) {
        this.id = id;
        this.executor = executor;
        this.runners = LevelStore.createWithLevel(level -> new Runner(this), Runner::close);
    }

    /**
     * 创建并登记全局唯一标识的异步车道。
     *
     * @param id       用于诊断和区分车道的标识
     * @param executor 提交异步调度轮次的执行器，生命周期由调用方管理
     * @return 新登记的车道
     * @throws NullPointerException     标识或执行器为空
     * @throws IllegalArgumentException 已有车道使用相同标识
     */
    public static synchronized AsyncLane create(String id, Executor executor) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(executor, "executor");
        for (AsyncLane lane : lanes) {
            if (lane.id.equals(id)) throw new IllegalArgumentException("duplicate async lane " + id);
        }
        AsyncLane lane = new AsyncLane(id, executor);
        AsyncLane[] grown = Arrays.copyOf(lanes, lanes.length + 1);
        grown[lanes.length] = lane;
        lanes = grown;
        return lane;
    }

    /**
     * 创建带指定名称前缀的守护型低优先级工作线程。
     *
     * @param name 线程名前缀
     * @return 用于异步车道的线程工厂
     */
    public static ThreadFactory threadFactory(String name) {
        AtomicInteger count = new AtomicInteger();
        return r -> {
            Thread t = new Worker(r, name + "-" + count.getAndIncrement());
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        };
    }

    /** 判断当前线程是否由 {@link #threadFactory(String)} 创建。 */
    public static boolean isWorkerThread() {
        return Thread.currentThread() instanceof Worker;
    }

    /** 返回该车道的诊断标识。 */
    public String id() {
        return id;
    }

    TickScheduler scheduler(Level level) {
        if (LevelStore.isReleased(level)) return TickScheduler.CLOSED;
        return runners.get(level).scheduler;
    }

    static void kickAll(Level level) {
        for (AsyncLane lane : lanes) {
            Runner runner = lane.runners.getIfPresent(level);
            if (runner != null) runner.kick();
        }
    }

    private static Executor defaultExecutor() {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), threadFactory("mmclutils-async"));
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    @Override
    public String toString() {
        return id;
    }

    private static final class Worker extends Thread {

        Worker(Runnable r, String name) {
            super(r, name);
        }
    }

    private static final class Runner implements Runnable {

        final AsyncLane lane;
        final TickScheduler scheduler;
        final AtomicBoolean running = new AtomicBoolean();
        long skipped;

        Runner(AsyncLane lane) {
            this.lane = lane;
            this.scheduler = new TickScheduler(false, (error, owner) -> LOGGER.error("异步调度 {} 执行任务失败，所有者为 {}", lane.id, owner, error));
        }

        void kick() {
            if (scheduler.isClosed() || !running.compareAndSet(false, true)) {
                if (!scheduler.isClosed()) skipped++;
                return;
            }
            if (scheduler.advanceIfIdle()) {
                running.set(false);
                return;
            }
            try {
                lane.executor.execute(this);
            } catch (RejectedExecutionException e) {
                running.set(false);
                LOGGER.warn("异步车道 {} 拒绝执行本轮任务", lane.id, e);
            }
        }

        @Override
        public void run() {
            try {
                scheduler.runRound();
            } finally {
                running.set(false);
            }
        }

        void close() {
            scheduler.close();
            if (running.compareAndSet(false, true)) {
                scheduler.clear();
                running.set(false);
            }
        }

        @Override
        public String toString() {
            return lane.id + " skipped=" + skipped;
        }
    }
}
