package com.modularmc.mmclutils.math;

import net.minecraft.util.RandomSource;

/** 使用调用者提供的随机源，便于重放抽样结果。 */
public final class RandomSampling {

    private RandomSampling() {}

    public static boolean chance(RandomSource random, double probability) {
        if (!(probability >= 0 && probability <= 1)) throw new IllegalArgumentException("概率应在零与一之间");
        return probability == 1 || probability > 0 && random.nextDouble() < probability;
    }

    public static int occurrences(RandomSource random, int trials, double probability) {
        if (trials < 0 || !(probability >= 0 && probability <= 1)) throw new IllegalArgumentException("试验次数或概率无效");
        if (probability == 0 || probability == 1) return probability == 0 ? 0 : trials;
        int successes = 0;
        for (int trial = 0; trial < trials; trial++) if (random.nextDouble() < probability) successes++;
        return successes;
    }
}
