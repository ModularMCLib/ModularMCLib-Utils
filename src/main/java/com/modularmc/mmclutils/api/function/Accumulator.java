package com.modularmc.mmclutils.api.function;

import lombok.Getter;

import java.util.function.BiFunction;
import java.util.function.Consumer;

/** 按接收顺序将输入累积到一个值中，可用作顺序流的消费端。 */
public final class Accumulator<T, U> implements Consumer<U> {

    private final BiFunction<? super T, ? super U, ? extends T> accumulate;
    /** 当前累计值，可通过 {@code getValue()} 读取。 */
    @Getter
    private T value;

    /** 用初值和结合函数创建累积器。每次接收输入时，将当前值与输入一起交给结合函数。 */
    public Accumulator(T initialValue, BiFunction<? super T, ? super U, ? extends T> accumulate) {
        this.value = initialValue;
        this.accumulate = accumulate;
    }

    @Override
    public void accept(U input) {
        value = accumulate.apply(value, input);
    }
}
