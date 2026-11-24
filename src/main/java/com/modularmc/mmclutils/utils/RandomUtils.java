package com.modularmc.mmclutils.utils;

import net.minecraft.util.RandomSource;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.ToLongFunction;

/** 使用调用者提供的随机源，便于重放抽样结果。 */
public final class RandomUtils {

    private RandomUtils() {}

    /** 以给定概率进行一次抽样。概率必须在 0～1 之间，两个端点直接返回。 */
    public static boolean chance(RandomSource random, double probability) {
        if (!(probability >= 0 && probability <= 1)) throw new IllegalArgumentException("概率应在零与一之间");
        return probability == 1 || probability > 0 && random.nextDouble() < probability;
    }

    /** 按指定概率独立试验，返回成功次数。次数须非负，概率须在 0～1 之间。 */
    public static int occurrences(RandomSource random, int trials, double probability) {
        if (trials < 0 || !(probability >= 0 && probability <= 1)) throw new IllegalArgumentException("试验次数或概率无效");
        if (probability == 0 || probability == 1) return probability == 0 ? 0 : trials;
        int successes = 0;
        for (int trial = 0; trial < trials; trial++) if (random.nextDouble() < probability) successes++;
        return successes;
    }

    /** 每次以 1 / oneIn 的概率计数，达到上限时停止取随机数。 */
    public static int occurrences(RandomSource random, int trials, int oneIn, int limit) {
        if (trials < 0 || oneIn <= 0 || limit < 0) throw new IllegalArgumentException("试验次数、概率分母或计数上限无效");
        int successes = 0;
        for (int trial = 0; trial < trials && successes < limit; trial++) {
            if (random.nextInt(oneIn) == 0) successes++;
        }
        return successes;
    }

    /**
     * 以权重比例抽取一个条目。权重函数对每个候选调用一次。
     *
     * @param random  调用方持有的随机源
     * @param entries 抽样期间保持不变的候选列表
     * @param weight  返回非负权重的函数
     * @param <T>     候选类型
     * @return 选中的条目，权重总和为零时返回空值
     * @throws IllegalArgumentException 发现负权重
     * @throws ArithmeticException      权重总和超出 long 范围
     */
    public static <T> @Nullable T chooseWeighted(RandomSource random, List<T> entries, ToLongFunction<? super T> weight) {
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

    /**
     * 用于带权抽样的候选及其非负权重。
     *
     * @param value  候选值
     * @param weight 相对于其他候选的权重，零表示不会抽中
     * @param <T>    候选类型
     */
    public record WeightedEntry<T>(T value, long weight) {

        public WeightedEntry {
            if (weight < 0) throw new IllegalArgumentException("权重应为非负数");
        }
    }
}
