package com.modularmc.mmclutils.api.function;

/** 接收三个参数的操作接口，用于避免在迭代中分配临时元组。 */
@FunctionalInterface
public interface TriConsumer<A, B, C> {

    /** 按声明顺序接收三个输入。空值约束和副作用由调用此接口的 API 定义。 */
    void accept(A a, B b, C c);
}
