package com.modularmc.mmclutils.api.schedule;

/**
 * 在调用边界保留可处理的异常，并让 JVM 致命错误继续传播。
 *
 * <p>
 * 适用于捕获 {@link Throwable} 的集成边界。
 * 调用方可对普通失败记录日志、回滚或隔离当前操作。
 */
public final class TaskFailures {

    private TaskFailures() {}

    /**
     * 重新抛出致命错误，并恢复中断异常携带的线程中断标记。
     *
     * <p>
     * 边界可以处理已捕获的 {@link InterruptedException}，线程中断标记仍应交还给调用方。
     *
     * @param throwable 调用过程中捕获的异常
     * @throws VirtualMachineError 捕获异常属于 JVM 致命错误
     */
    public static void rethrowIfFatal(Throwable throwable) {
        if (throwable instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
        if (throwable instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
