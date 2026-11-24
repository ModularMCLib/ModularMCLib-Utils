package com.modularmc.mmclutils.api.schedule;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * 在同一个工作线程按队列顺序执行操作，适合需要保序的写盘或其他后台工作。
 * 提交可来自多个线程。由执行器的所有者在工作线程之外关闭队列。
 */
public final class SerialTaskExecutor implements AutoCloseable {

    private final ExecutorService executor;

    /** 创建串行队列，使用指定名称启动虚拟线程。 */
    public SerialTaskExecutor(String name) {
        executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name(name).factory());
    }

    /**
     * 排队执行一个操作，将返回值或执行异常交给结果对象。
     * 取消结果对象只影响结果的接收，已提交的操作仍会执行。
     *
     * @param <T>       操作的结果类型
     * @param operation 在后台线程执行的操作
     * @return 与本次操作关联的结果对象
     * @throws RejectedExecutionException 队列已关闭
     */
    public <T> CompletableFuture<T> submit(Callable<T> operation) {
        var result = new CompletableFuture<T>();
        executor.execute(() -> {
            try {
                result.complete(operation.call());
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            } catch (Error error) {
                result.completeExceptionally(error);
                throw error;
            }
        });
        return result;
    }

    /** 停止接收任务并等待已经提交的操作执行完毕。 */
    @Override
    public void close() {
        executor.close();
    }
}
