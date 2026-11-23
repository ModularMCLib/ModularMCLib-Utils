package com.modularmc.mmclutils.collection.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** 按相等策略驻留或查找对象。驻留条目由该实例持有。 */
public final class HashInterner<T> implements Interner<T> {

    private final ConcurrentHashMap<T, T> map;
    private final Function<T, T> function = Function.identity();

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
