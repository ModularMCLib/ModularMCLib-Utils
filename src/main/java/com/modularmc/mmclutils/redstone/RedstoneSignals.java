package com.modularmc.mmclutils.redstone;

import com.modularmc.mmclutils.math.NumberMath;

import java.math.BigInteger;

public class RedstoneSignals {

    /**
     * 将数值映射到最小值和最大值之间的红石信号，支持反转。
     *
     * @param value      待映射数值
     * @param maxValue   最大值
     * @param minValue   最小值
     * @param isInverted 是否反转输出
     * @return 数值在边界内时按比例返回 0 至 15 的信号，越界时取端点信号
     */

    public static int computeRedstoneBetweenValues(float value, float maxValue, float minValue, boolean isInverted) {
        if (value >= maxValue) return isInverted ? 0 : 15;
        else if (value <= minValue) return isInverted ? 15 : 0;

        float ratio;
        if (isInverted) ratio = 15 * (maxValue - value) / (maxValue - minValue);
        else ratio = 15 * (value - minValue) / (maxValue - minValue);

        return Math.round(ratio);
    }

    public static int computeRedstoneBetweenValues(BigInteger value, BigInteger maxValue, BigInteger minValue,
                                                   boolean isInverted) {
        if (value.compareTo(maxValue) >= 0) return isInverted ? 0 : 15;
        else if (value.compareTo(maxValue) <= 0) return isInverted ? 15 : 0;

        float ratio;
        if (isInverted) ratio = 15 * NumberMath.ratio(maxValue.subtract(value), maxValue.subtract(minValue));
        else ratio = 15 * NumberMath.ratio(value.subtract(minValue), maxValue.subtract(minValue));

        return Math.round(ratio);
    }

    /**
     * 使用最小值和最大值构成迟滞区间，并在区间内维持输出。
     *
     * @param value    待比较数值
     * @param maxValue 上阈值
     * @param minValue 下阈值
     * @param output   当前的输出信号
     * @return 更新后的输出信号
     */
    public static int computeLatchedRedstoneBetweenValues(float value, float maxValue, float minValue,
                                                          boolean isInverted, int output) {
        if (value >= maxValue) output = isInverted ? 15 : 0; // value above maxValue should normally be 0, otherwise 15
        else if (value <= minValue) output = isInverted ? 0 : 15; // value below minValue should normally be 15,
                                                                  // 其余情况清空信号。
        return output;
    }

    public static int computeLatchedRedstoneBetweenValues(BigInteger value, BigInteger maxValue, BigInteger minValue,
                                                          boolean isInverted, int output) {
        if (value.compareTo(maxValue) >= 0) output = isInverted ? 15 : 0;
        else if (value.compareTo(minValue) <= 0) output = isInverted ? 0 : 15;
        return output;
    }

    /**
     * 按当前值占上限的比例计算红石信号，支持反转。
     *
     * @param current    当前值
     * @param max        最大值
     * @param isInverted 是否反转输出
     * @return 0 至 15 的信号。当前值为正数时，反转前至少为 1
     * @throws ArithmeticException 除数为零
     */
    public static int computeRedstoneValue(long current, long max, boolean isInverted) throws ArithmeticException {
        var output = (int) (14f * current / max) + (current > 0 ? 1 : 0);
        return isInverted ? 15 - output : output;
    }

    public static int computeRedstoneValue(BigInteger current, BigInteger max,
                                           boolean isInverted) throws ArithmeticException {
        var isNotEmpty = current.compareTo(BigInteger.ZERO) > 0;
        var output = (int) (14f * NumberMath.ratio(current, max)) + (isNotEmpty ? 1 : 0);
        return isInverted ? 15 - output : output;
    }
}
