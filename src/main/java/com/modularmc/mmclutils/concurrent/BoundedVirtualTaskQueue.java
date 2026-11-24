package com.modularmc.mmclutils.concurrent;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 由所属线程提交、轮询和取消虚拟线程任务。结果队列有容量限制，计算线程只使用 Work 上下文。
 * 结果发出后归队列所有，轮询交付后由调用方释放。
 * 作废结果可能在计算线程或所属线程释放。释放动作须支持并发调用，并可脱离 GPU 上下文执行。
 */
public final class BoundedVirtualTaskQueue<T> implements AutoCloseable {

    private final int parallelism;
    private final Consumer<T> release;
    private final ExecutorService executor;
    private final Semaphore workers;
    private final Semaphore resultSlots;
    private final Object handoff = new Object();
    private final ConcurrentLinkedQueue<Envelope<T>> results = new ConcurrentLinkedQueue<>();
    private final Long2ObjectOpenHashMap<Ticket> tasks = new Long2ObjectOpenHashMap<>();
    private long generation;
    private boolean closed;

    /** 创建队列。并发数与结果容量必须为正数，线程名前缀用于识别所属功能。 */
    public BoundedVirtualTaskQueue(String threadPrefix, int parallelism, int resultCapacity, Consumer<T> release) {
        if (parallelism < 1 || resultCapacity < 1) throw new IllegalArgumentException("Task queue limits must be positive");
        this.parallelism = parallelism;
        this.release = release;
        workers = new Semaphore(parallelism);
        resultSlots = new Semaphore(resultCapacity);
        executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(threadPrefix, 0).factory());
    }

    /** 在所属线程查询提交容量。已完成的任务在终止结果被取走之前仍占用名额。 */
    public boolean hasCapacity() {
        return !closed && tasks.size() < parallelism && workers.availablePermits() > 0;
    }

    /** 查询指定原始类型键当前是否有任务。 */
    public boolean contains(long key) {
        return tasks.containsKey(key);
    }

    /** 在所属线程提交任务。调用方须确认容量，重复的键须先取消旧任务。 */
    public void submit(long key, Consumer<Work<T>> calculation) {
        if (!hasCapacity() || tasks.containsKey(key) || !workers.tryAcquire()) throw new IllegalStateException("No task slot or duplicate task key: " + key);
        var ticket = new Ticket(key, generation);
        var work = new Work<>(this, ticket);
        FutureTask<Boolean> future = new FutureTask<>(() -> {
            try {
                work.checkCancelled();
                calculation.accept(work);
                work.publish(null, null, true);
            } catch (RuntimeException | Error failure) {
                if (ticket.cancelled.get() || Thread.currentThread().isInterrupted()) ticket.cancelled.set(true);
                else {
                    try {
                        work.publish(null, failure, true);
                    } catch (CancellationException stopped) {
                        ticket.cancelled.set(true);
                    }
                }
            }
            return Boolean.TRUE;
        });
        ticket.future = future;
        tasks.put(key, ticket);
        try {
            executor.execute(() -> {
                if (!ticket.started.compareAndSet(false, true)) return;
                try {
                    future.run();
                } finally {
                    workers.release();
                }
            });
        } catch (RuntimeException | Error failure) {
            tasks.remove(key);
            if (ticket.started.compareAndSet(false, true)) workers.release();
            throw failure;
        }
    }

    /**
     * 在所属线程轮询结果。普通结果一经返回，调用方负责释放其值。
     * 终止结果可以携带计算异常。已取消或作废的结果由队列释放。
     */
    public @Nullable Result<T> poll() {
        Envelope<T> envelope;
        while ((envelope = results.poll()) != null) {
            resultSlots.release();
            Ticket ticket = envelope.ticket();
            Result<T> result = envelope.result();
            if (ticket.generation != generation || !tasks.containsKey(ticket.key) || tasks.get(ticket.key) != ticket) {
                if (result.value() != null) release.accept(result.value());
                continue;
            }
            if (result.terminal()) tasks.remove(ticket.key);
            return result;
        }
        var iterator = tasks.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Ticket ticket = entry.getValue();
            if (ticket.cancelled.get() && ticket.future.isDone()) {
                long key = entry.getLongKey();
                iterator.remove();
                return new Result<>(key, null, new CancellationException("Worker stopped without publishing a result"), true);
            }
        }
        return null;
    }

    /** 作废指定键并中断计算，立即释放尚未取走的结果。 */
    public void cancel(long key) {
        if (!tasks.containsKey(key)) return;
        Ticket ticket = tasks.remove(key);
        synchronized (handoff) {
            ticket.cancelled.set(true);
            ticket.future.cancel(true);
            if (ticket.started.compareAndSet(false, true)) workers.release();
            results.removeIf(envelope -> {
                if (envelope.ticket() != ticket) return false;
                if (envelope.result().value() != null) release.accept(envelope.result().value());
                resultSlots.release();
                return true;
            });
        }
    }

    /** 切换到下一代，取消全部任务并释放队列中的结果。 */
    public void clear() {
        generation++;
        for (long key : tasks.keySet().toLongArray()) cancel(key);
    }

    /** 关闭任务入口并中断计算线程。所属线程可立即返回。 */
    @Override
    public void close() {
        clear();
        closed = true;
        executor.shutdownNow();
    }

    /** 计算线程使用的任务键、取消状态和结果交付入口。 */
    public static final class Work<T> {

        private final BoundedVirtualTaskQueue<T> owner;
        private final Ticket ticket;
        @Getter
        private final long key;

        private Work(BoundedVirtualTaskQueue<T> owner, Ticket ticket) {
            this.owner = owner;
            this.ticket = ticket;
            key = ticket.key;
        }

        /** 供计算循环检查取消或中断状态。 */
        public boolean cancelled() {
            return ticket.cancelled.get() || Thread.currentThread().isInterrupted();
        }

        /** 任务作废或计算线程中断时抛出取消异常。 */
        public void checkCancelled() {
            if (cancelled()) throw new CancellationException();
        }

        /**
         * 交付一个非空结果，队列满时可中断地等待。结果一经传入即转交所有权。
         * 若交付因取消或其它异常失败，由该方法释放结果。
         */
        public void emit(T value) {
            try {
                publish(value, null, false);
            } catch (RuntimeException | Error failure) {
                owner.release.accept(value);
                throw failure;
            }
        }

        private void publish(@Nullable T value, @Nullable Throwable failure, boolean terminal) {
            checkCancelled();
            try {
                owner.resultSlots.acquire();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new CancellationException();
            }
            boolean transferred = false;
            try {
                synchronized (owner.handoff) {
                    checkCancelled();
                    owner.results.add(new Envelope<>(ticket, new Result<>(key, value, failure, terminal)));
                    transferred = true;
                }
            } finally {
                if (!transferred) owner.resultSlots.release();
            }
        }
    }

    /** 所属线程接收的结果。数据值出现在普通结果中，异常出现在失败的终止结果中。 */
    public record Result<T>(long key, @Nullable T value, @Nullable Throwable failure, boolean terminal) {}

    private record Envelope<T>(Ticket ticket, Result<T> result) {}

    private static final class Ticket {

        private final long key;
        private final long generation;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean started = new AtomicBoolean();
        private FutureTask<Boolean> future = new FutureTask<>(() -> Boolean.FALSE);

        private Ticket(long key, long generation) {
            this.key = key;
            this.generation = generation;
        }
    }
}
