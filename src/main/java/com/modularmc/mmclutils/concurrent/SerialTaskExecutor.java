package com.modularmc.mmclutils.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 单工作者按提交顺序执行操作。关闭时等待已接收的操作执行完毕。 */
public final class SerialTaskExecutor implements AutoCloseable {

    private final ExecutorService executor;

    public SerialTaskExecutor(String name) {
        executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name(name).factory());
    }

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

    @Override
    public void close() {
        executor.close();
    }
}
