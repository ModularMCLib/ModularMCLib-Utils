package com.modularmc.mmclutils.api.collection.cache;

import java.util.function.UnaryOperator;

/** 驻留相等对象并返回共享实例。弱引用实现允许无外部引用的实例被回收。 */
public interface Interner<T> {

    UnaryOperator<Object> IDENTITY_OPERATOR = UnaryOperator.identity();

    @SuppressWarnings("unchecked")
    static <T> UnaryOperator<T> identityMappingFunction() {
        return (UnaryOperator<T>) IDENTITY_OPERATOR;
    }

    T intern(final T sample);

    T intern(final T sample, UnaryOperator<T> mappingFunction);

    boolean isPresent(final T sample);

    boolean addIfAbsent(final T sample);

    void clear();
}
