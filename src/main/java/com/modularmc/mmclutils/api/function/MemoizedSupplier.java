package com.modularmc.mmclutils.api.function;

import java.util.function.Supplier;

/**
 * 首次取值后保留结果的 Supplier，可由调用方使缓存失效并重新计算。
 * 实例和缓存值须由一个线程访问，类本身不提供并发同步。
 *
 * @param <T> 供应的值类型
 */
public class MemoizedSupplier<T> implements Supplier<T> {

    /** 最近一次成功计算得到的值，由 {@link #invalidate()} 清空。 */
    protected T value = null;
    /** 记录委托是否已成功计算，允许把空值作为计算结果缓存。 */
    protected boolean initialized = false;
    /** 缓存尚未建立时调用的供应器。 */
    protected final Supplier<T> delegate;

    /** 包装延迟执行的委托。调用 {@link #get()} 前不会运行委托。 */
    protected MemoizedSupplier(Supplier<T> delegate) {
        this.delegate = delegate;
    }

    /** 计算并返回缓存值，首次调用时委托给原始 Supplier。 */
    @Override
    public T get() {
        if (!initialized) {
            value = delegate.get();
            initialized = true;
        }
        return value;
    }

    /** 丢弃已有值，使下次 {@link #get()} 重新调用原始 Supplier。 */
    public void invalidate() {
        initialized = false;
        value = null;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + (initialized ? value : "Uninitialized") + ")";
    }
}
