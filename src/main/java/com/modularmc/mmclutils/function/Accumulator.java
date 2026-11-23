package com.modularmc.mmclutils.function;

import java.util.function.BiFunction;
import java.util.function.Consumer;

/** 按接收顺序将输入累积到一个值中，可用作顺序流的消费端。 */
public final class Accumulator<T, U> implements Consumer<U> {

    private final BiFunction<? super T, ? super U, ? extends T> accumulate;
    private T value;

    public Accumulator(T initialValue, BiFunction<? super T, ? super U, ? extends T> accumulate) {
        this.value = initialValue;
        this.accumulate = accumulate;
    }

    @Override
    public void accept(U input) {
        value = accumulate.apply(value, input);
    }

    public T getValue() {
        return value;
    }
}
