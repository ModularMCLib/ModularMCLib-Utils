package com.modularmc.mmclutils.function;

import java.util.function.BiConsumer;

/**
 * 接收原始 int 和对象的消费端。可直接用于按索引处理对象的循环，避免装箱。
 * 并发访问与输入是否可空由具体消费端的契约决定。
 */
@FunctionalInterface
public interface IntObjectConsumer<T> extends BiConsumer<Integer, T> {

    /**
     * 对两个输入执行消费动作。
     *
     * @param t 第一个输入
     * @param u 第二个输入
     */
    void accept(int t, T u);

    /**
     * {@inheritDoc}
     *
     * @deprecated 优先调用原始类型重载
     */
    @Deprecated
    @Override
    default void accept(Integer integer, T u) {
        accept((int) integer, u);
    }
}
