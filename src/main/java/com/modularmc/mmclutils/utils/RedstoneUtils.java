package com.modularmc.mmclutils.utils;

import java.math.BigInteger;

/** 将数值和阈值转换为红石强度，支持反向输出和保持前次状态的迟滞。 */
public class RedstoneUtils {

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

    /** 以任意精度整数给出上下限和当前值，按比例得到 0～15 的信号。 */
    public static int computeRedstoneBetweenValues(BigInteger value, BigInteger maxValue, BigInteger minValue,
                                                   boolean isInverted) {
        if (value.compareTo(maxValue) >= 0) return isInverted ? 0 : 15;
        else if (value.compareTo(maxValue) <= 0) return isInverted ? 15 : 0;

        float ratio;
        if (isInverted) ratio = 15 * MathUtils.ratio(maxValue.subtract(value), maxValue.subtract(minValue));
        else ratio = 15 * MathUtils.ratio(value.subtract(minValue), maxValue.subtract(minValue));

        return Math.round(ratio);
    }

    /**
     * 使用最小值和最大值构成迟滞区间，并在区间内维持输出。
     *
     * @param value      待比较数值
     * @param maxValue   上阈值
     * @param minValue   下阈值
     * @param isInverted 是否反转阈值对应的信号
     * @param output     当前的输出信号
     * @return 更新后的输出信号
     */
    public static int computeLatchedRedstoneBetweenValues(float value, float maxValue, float minValue,
                                                          boolean isInverted, int output) {
        if (value >= maxValue) output = isInverted ? 15 : 0;
        else if (value <= minValue) output = isInverted ? 0 : 15;
        return output;
    }

    /** 达到上下阈值时切换红石信号，处于迟滞区间时保留传入的信号。 */
    public static int computeLatchedRedstoneBetweenValues(BigInteger value, BigInteger maxValue, BigInteger minValue,
                                                          boolean isInverted, int output) {
        if (value.compareTo(maxValue) >= 0) output = isInverted ? 15 : 0;
        else if (value.compareTo(minValue) <= 0) output = isInverted ? 0 : 15;
        return output;
    }

    /**
     * 按当前值占上限的比例计算红石信号，支持反转。
     *
     * @param current    当前值，范围为零到上限
     * @param max        正数上限
     * @param isInverted 是否反转输出
     * @return 0 至 15 的信号。当前值为正数时，反转前至少为 1
     * @throws ArithmeticException 除数为零
     */
    public static int computeRedstoneValue(long current, long max, boolean isInverted) throws ArithmeticException {
        if (max == 0) throw new ArithmeticException("红石信号上限为零");
        var output = (int) (14f * current / max) + (current > 0 ? 1 : 0);
        return isInverted ? 15 - output : output;
    }

    /** 按任意精度当前值与正上限的比例生成信号，当前值应在 0 到上限之间。 */
    public static int computeRedstoneValue(BigInteger current, BigInteger max,
                                           boolean isInverted) throws ArithmeticException {
        var isNotEmpty = current.compareTo(BigInteger.ZERO) > 0;
        var output = (int) (14f * MathUtils.ratio(current, max)) + (isNotEmpty ? 1 : 0);
        return isInverted ? 15 - output : output;
    }
}
