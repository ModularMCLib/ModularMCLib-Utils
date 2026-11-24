package com.modularmc.mmclutils.utils;

import net.minecraft.util.Mth;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/** 数值范围、插值和数值转换。 */
public final class MathUtils {

    private MathUtils() {}

    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int lerpInt(double delta, int start, int end) {
        return start + Mth.floor(delta * (end - start));
    }

    public static int[] split(long value) {
        IntArrayList result = new IntArrayList();
        while (value > 0) {
            int intValue = (int) Math.min(value, Integer.MAX_VALUE);
            result.add(intValue);
            value -= intValue;
        }
        return result.toIntArray();
    }

    public static int saturatedCast(long value) {
        if (value > 2147483647L) {
            return Integer.MAX_VALUE;
        } else {
            return value < -2147483648L ? Integer.MIN_VALUE : (int) value;
        }
    }

    public static float ratio(BigInteger a, BigInteger b) {
        return new BigDecimal(a).divide(new BigDecimal(b), MathContext.DECIMAL32).floatValue();
    }

    public static int ceilDiv(int x, int y) {
        final int q = x / y;
        // 同号且存在余数时向上取整。
        if ((x ^ y) >= 0 && (q * y != x)) {
            return q + 1;
        }
        return q;
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

    public static float min(float @NonNull... values) {
        // noinspection ConstantValue JSpecify 将数组声明为非空，保留范围检查。
        if (values == null || values.length == 0) throw new IllegalArgumentException();
        if (values.length == 1) return values[0];
        if (values.length == 2) return Math.min(values[0], values[1]);
        float min = Float.MAX_VALUE;
        for (float i : values) {
            if (i < min) {
                min = i;
            }
        }
        return min;
    }

    public static float max(float @NonNull... values) {
        // noinspection ConstantValue JSpecify 将数组声明为非空，保留范围检查。
        if (values == null || values.length == 0) throw new IllegalArgumentException();
        if (values.length == 1) return values[0];
        if (values.length == 2) return Math.max(values[0], values[1]);
        float max = -Float.MAX_VALUE;
        for (float i : values) {
            if (i > max) {
                max = i;
            }
        }
        return max;
    }
}
