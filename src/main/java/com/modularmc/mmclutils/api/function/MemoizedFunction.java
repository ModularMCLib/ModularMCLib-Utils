package com.modularmc.mmclutils.api.function;

import java.util.Map;
import java.util.function.Function;

/**
 * 对函数结果提供可访问缓存的函数契约。
 * 缓存键的比较、引用强弱和线程安全由具体实现决定。
 *
 * @param <T> 输入参数类型
 * @param <R> 计算结果类型
 */
public interface MemoizedFunction<T, R> extends Function<T, R> {

    /**
     * 返回该函数实际使用的结果缓存。
     * 修改或清空此映射会影响后续调用，映射的并发安全由实现决定。
     *
     * @return 参数到计算结果的映射
     */
    Map<T, R> getCache();
}
