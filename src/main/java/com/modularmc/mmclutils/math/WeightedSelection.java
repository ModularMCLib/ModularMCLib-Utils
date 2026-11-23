package com.modularmc.mmclutils.math;

import net.minecraft.util.RandomSource;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.ToLongFunction;

/** 根据非负权重抽取条目。无正权重条目时返回空值。 */
public final class WeightedSelection {

    private WeightedSelection() {}

    public static <T> @Nullable T choose(RandomSource random, List<T> entries, ToLongFunction<? super T> weight) {
        long[] weights = new long[entries.size()];
        long total = 0;
        for (int index = 0; index < entries.size(); index++) {
            long current = weight.applyAsLong(entries.get(index));
            if (current < 0) throw new IllegalArgumentException("权重应为非负数");
            weights[index] = current;
            total = Math.addExact(total, current);
        }
        if (total == 0) return null;
        long draw = boundedLong(random, total);
        for (int index = 0; index < weights.length; index++) {
            draw -= weights[index];
            if (draw < 0) return entries.get(index);
        }
        throw new IllegalStateException("权重数量发生变化");
    }

    private static long boundedLong(RandomSource random, long bound) {
        long mask = bound - 1;
        long bits = random.nextLong() >>> 1;
        if ((bound & mask) == 0) return bits & mask;
        long value;
        for (; bits - (value = bits % bound) + mask < 0; bits = random.nextLong() >>> 1) {}
        return value;
    }

    public record Entry<T>(T value, long weight) {

        public Entry {
            if (weight < 0) throw new IllegalArgumentException("权重应为非负数");
        }
    }
}
