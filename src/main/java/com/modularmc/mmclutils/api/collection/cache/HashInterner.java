package com.modularmc.mmclutils.api.collection.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * 按值相等性驻留对象。驻留条目由该实例强引用。
 *
 * @param <T> 驻留对象类型
 */
public final class HashInterner<T> implements Interner<T> {

    private final ConcurrentHashMap<T, T> map;
    private final Function<T, T> function = Function.identity();

    /** 按值的相等性创建驻留表，已登记者保留强引用。 */
    public HashInterner() {
        this.map = new ConcurrentHashMap<>();
    }

    @Override
    public T intern(final T sample) {
        return map.computeIfAbsent(sample, function);
    }

    @Override
    public T intern(T sample, UnaryOperator<T> mappingFunction) {
        var v = map.get(sample);
        if (v != null) {
            return v;
        }
        v = mappingFunction.apply(sample);
        var prev = map.putIfAbsent(v, v);
        return prev == null ? v : prev;
    }

    @Override
    public boolean isPresent(final T sample) {
        return map.containsKey(sample);
    }

    @Override
    public boolean addIfAbsent(final T sample) {
        return map.putIfAbsent(sample, sample) == null;
    }

    @Override
    public void clear() {
        map.clear();
    }
}
