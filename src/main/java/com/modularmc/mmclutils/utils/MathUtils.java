package com.modularmc.mmclutils.utils;

import net.minecraft.util.Mth;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/** 数值范围、插值和数值转换。 */
public final class MathUtils {

    private MathUtils() {}

    /** 将数值限制在包含两端的区间内。下限须小于或等于上限。 */
    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 按比例插值，对结果向下取整。比例在 0～1 之外时进行外推。 */
    public static int lerpInt(double delta, int start, int end) {
        return start + Mth.floor(delta * (end - start));
    }

    /** 将正整数依次分成尽可能大的正 int。非正数返回空数组。 */
    public static int[] split(long value) {
        IntArrayList result = new IntArrayList();
        while (value > 0) {
            int intValue = (int) Math.min(value, Integer.MAX_VALUE);
            result.add(intValue);
            value -= intValue;
        }
        return result.toIntArray();
    }

    /** 转换为 int。超出表示范围时返回相应端点。 */
    public static int saturatedCast(long value) {
        return Math.clamp(value, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /** 按 {@link MathContext#DECIMAL32} 计算比例并转为 float。除数为零时抛出算术异常。 */
    public static float ratio(BigInteger a, BigInteger b) {
        return new BigDecimal(a).divide(new BigDecimal(b), MathContext.DECIMAL32).floatValue();
    }

    /** 整数除法向上取整，除数为零时抛出算术异常。 */
    public static int ceilDiv(int x, int y) {
        return Math.ceilDiv(x, y);
    }

    /** 非负被除数除以正除数，余数非零时向上取整。 */
    public static BigInteger ceilDivNonNegative(BigInteger dividend, BigInteger divisor) {
        if (dividend.signum() < 0 || divisor.signum() <= 0) {
            throw new IllegalArgumentException("被除数须为非负数，除数须为正数");
        }
        BigInteger[] division = dividend.divideAndRemainder(divisor);
        return division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
    }

    /** 两个操作数须为非负数，和超出 long 上限时取最大值。 */
    public static long saturatedAddNonNegative(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    /** 两个操作数须为非负数，积超出 long 上限时取最大值。 */
    public static long saturatedMultiplyNonNegative(long left, long right) {
        return left != 0 && right > Long.MAX_VALUE / left ? Long.MAX_VALUE : left * right;
    }

    /**
     * 取最小浮点值，遵循 {@link Math#min(float, float)} 的 NaN、无穷和正负零规则。
     *
     * @param values 至少一个浮点值
     * @return 最小值，输入含 NaN 时返回 NaN
     * @throws IllegalArgumentException 数组没有元素
     */
    public static float min(float... values) {
        if (values.length == 0) throw new IllegalArgumentException("至少需要一个值");
        float result = values[0];
        for (int i = 1; i < values.length; i++) result = Math.min(result, values[i]);
        return result;
    }

    /**
     * 取最大浮点值，遵循 {@link Math#max(float, float)} 的 NaN、无穷和正负零规则。
     *
     * @param values 至少一个浮点值
     * @return 最大值，输入含 NaN 时返回 NaN
     * @throws IllegalArgumentException 数组没有元素
     */
    public static float max(float... values) {
        if (values.length == 0) throw new IllegalArgumentException("至少需要一个值");
        float result = values[0];
        for (int i = 1; i < values.length; i++) result = Math.max(result, values[i]);
        return result;
    }
}
