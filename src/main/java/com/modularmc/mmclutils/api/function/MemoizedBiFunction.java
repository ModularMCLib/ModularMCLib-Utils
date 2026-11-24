package com.modularmc.mmclutils.api.function;

import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * 对两个输入参数的函数提供可访问缓存的契约。
 * 参数对按 {@link Pair} 表示，缓存键的比较、引用强弱和线程安全由具体实现决定。
 *
 * @param <T> 第一个输入参数类型
 * @param <U> 第二个输入参数类型
 * @param <R> 计算结果类型
 */
public interface MemoizedBiFunction<T, U, R> extends BiFunction<T, U, R> {

    /**
     * 返回该函数实际使用的结果缓存。
     * 修改或清空此映射会影响后续调用，映射的并发安全由实现决定。
     *
     * @return 参数对到计算结果的映射
     */
    Map<Pair<T, U>, R> getCache();
}
