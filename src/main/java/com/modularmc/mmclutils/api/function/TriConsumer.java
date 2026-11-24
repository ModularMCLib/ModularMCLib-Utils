package com.modularmc.mmclutils.api.function;

/** 接收三个参数的操作接口，用于避免在迭代中分配临时元组。 */
@FunctionalInterface
public interface TriConsumer<A, B, C> {

    void accept(A a, B b, C c);
}
