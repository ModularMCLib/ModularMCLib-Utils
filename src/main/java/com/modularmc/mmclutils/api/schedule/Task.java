package com.modularmc.mmclutils.api.schedule;

import org.jspecify.annotations.Nullable;

/**
 * 一次性或周期任务的取消与周期查询契约。
 * 任务生命周期与执行线程由创建它的调度器控制。
 */
public interface Task {

    /** 判断任务是否仍有效并可执行。 */
    boolean isActive();

    /** 使任务失效；已经开始执行的操作不会被强制中断。 */
    void cancel();

    /** 返回周期 Tick 数，一次性任务返回零。 */
    int period();

    /**
     * 请求更改周期任务的执行间隔。
     * 新间隔在当前执行结束后生效，一次性任务不支持修改。
     *
     * @param period 新周期 Tick 数，低于一的数值按一处理
     * @throws IllegalStateException 当前任务不支持周期修改
     */
    void setPeriod(int period);

    /**
     * 取消非空任务并返回空值，便于在同一赋值中清空句柄。
     *
     * @param task 要取消的任务，可以为空
     * @return 始终为空
     */
    static @Nullable Task cancel(@Nullable Task task) {
        if (task != null) task.cancel();
        return null;
    }

    /**
     * 判断非空任务是否仍有效。
     *
     * @param task 要查询的任务，可以为空
     * @return 任务非空且有效时为 {@code true}
     */
    static boolean isActive(@Nullable Task task) {
        return task != null && task.isActive();
    }
}
